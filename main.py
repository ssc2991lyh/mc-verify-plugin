"""
Minecraft QQ 群验证码白名单门禁插件 (astrbot_plugin_mc_verify)

v3 重构后的角色（与 MCMultiLoginCompat Java 插件协作，跨机部署）：
  - Java 插件（MC 服，Linux）是「门禁权威方 + 验证状态持有方」：
    玩家进服时生成验证码、写入 MC 服本地的 verify.json，未验证则 /kick，
    并提供「按验证码标记已验证」的入口（/multilogin verify <code>）。
  - 本插件（AstrBot 端，Windows）是「QQ 群验证码转发端」：
    监听群消息「验证 XXXX」→ 经 AstrBotAdapter REST command/execute
    把 /multilogin verify <code> 发到 MC 服执行 → 收到成功回调 → 回群「验证成功」。

  ⚠️ 本插件【不】写任何 json、不轮询、不冻结：所有状态都在 MC 服本地，
     跨机无法共享文件，因此只做「转发 + 收回调」。

依赖：
  - 已安装并运行 AstrBot（提供 QQ 群消息事件）
  - MC 服已装 AstrBotAdapter（提供 REST :8765 与 token，用于远程执行指令）
  - MC 服已装 MCMultiLoginCompat（提供 /multilogin verify 指令）
"""

import asyncio
import json
import re

import aiohttp
from astrbot.api import logger
from astrbot.api.event import filter, AstrMessageEvent
from astrbot.api.star import Context, Star, register

PLUGIN_NAME = "astrbot_plugin_mc_verify"

# 验证码格式：与 Java 端 VerifyState.CODE_ALPHABET 解出的码一致（4~8 位字母数字）
CODE_RE = re.compile(r"^验证\s*([A-Za-z0-9]{4,8})$", re.IGNORECASE)


@register(
    PLUGIN_NAME,
    "慕洛清",
    "Minecraft QQ群验证码白名单门禁：监听「验证 XXXX」后经 AstrBotAdapter 转发到 MC 服标记（不写本地 json）",
    "3.0.0",
    "https://github.com/",
)
class MinecraftVerifyPlugin(Star):
    def __init__(self, context: Context, config: dict):
        super().__init__(context)
        self.config = config

        # ---- AstrBotAdapter（MC 服）连接信息 ----
        # MC 服与 AstrBot 同机填 127.0.0.1；否则填 MC 服 IP。
        self.mc_host = str(config.get("mc_host", "127.0.0.1")).strip() or "127.0.0.1"
        self.mc_rest_port = int(config.get("mc_rest_port", 8765))
        self.astrbot_token = str(config.get("astrbottoken", "")).strip()
        self.base_url = f"http://{self.mc_host}:{self.mc_rest_port}/api/v1"

        # ---- QQ 群监听 ----
        self.group_id = str(config.get("group_id", "")).strip()
        self.group_verify_listen = bool(config.get("group_verify_listen", True))

        self._session: aiohttp.ClientSession | None = None

        if not self.astrbot_token:
            logger.warning(
                f"[{PLUGIN_NAME}] astrbottoken 为空！请在 AstrBotAdapter 配置"
                f"（MC 服 plugins/AstrbotAdapter/config.yml 的自动生成 token）填入后重启。"
            )
        if not self.group_id:
            logger.warning(
                f"[{PLUGIN_NAME}] 未配置 group_id，验证指令将在任意群生效，建议填入与 MC 服绑定的群号。"
            )
        logger.info(
            f"[{PLUGIN_NAME}] 已加载（转发模式），通道={self.base_url}，"
            f"监听群={self.group_id or '未配置'}，group_verify_listen={self.group_verify_listen}"
        )

    # ===================== 工具 =====================
    async def _session_ok(self) -> aiohttp.ClientSession:
        if self._session is None or self._session.closed:
            self._session = aiohttp.ClientSession()
        return self._session

    # ===================== 群消息：验证 =====================
    @filter.event_message_type(filter.EventMessageType.GROUP_MESSAGE)
    async def on_group_message(self, event: AstrMessageEvent, *args, **kwargs):
        if not self.group_verify_listen:
            return

        text = (event.message_str or "").strip()
        m = CODE_RE.match(text)
        if not m:
            return  # 不是验证消息，交给其它处理器

        # 限定在配置的群里
        if self.group_id and str(event.get_group_id()) != self.group_id:
            return

        code = m.group(1).upper()

        # 转发到 MC 服执行 /multilogin verify <code>（AstrBotAdapter command/execute）
        ok, detail = await self._forward_verify(code)
        if ok:
            event.stop_event()
            yield event.plain_result(
                "✅ 验证成功！已绑定 QQ，可以重新进服啦~"
            )
        else:
            event.stop_event()
            yield event.plain_result(
                f"❌ 验证失败：{detail}\n请确认验证码正确且未过期（过期请重进服获取新码）。"
            )

    async def _forward_verify(self, code: str):
        """经 AstrBotAdapter 把 /multilogin verify <code> 发到 MC 服执行，返回 (是否成功, 说明)。"""
        try:
            session = await self._session_ok()
            url = self.base_url + "/command/execute"
            headers = {"Content-Type": "application/json"}
            if self.astrbot_token:
                headers["Authorization"] = "Bearer " + self.astrbot_token
            payload = {
                "command": f"multilogin verify {code}",
                "executor": "CONSOLE",
            }
            async with session.post(url, json=payload, headers=headers, timeout=15) as resp:
                text = await resp.text()
                try:
                    data = json.loads(text)
                except Exception:
                    return False, f"MC 服返回非 JSON（HTTP {resp.status}）"

            # AstrBotAdapter 成功：code==0，data.success==true，data.output 含「验证成功」
            if data.get("code") != 0:
                return False, data.get("message", "MC 服拒绝执行")
            inner = data.get("data") or {}
            output = str(inner.get("output", ""))
            if inner.get("success") and ("验证成功" in output):
                return True, output
            if inner.get("success"):
                # 指令执行成功但未匹配到码（例如无效/过期）
                return False, output or "验证码无效或已过期"
            return False, output or "指令执行失败"
        except asyncio.TimeoutError:
            return False, "转发到 MC 服超时（请检查 AstrBotAdapter 是否在线）"
        except Exception as e:  # noqa: BLE001
            logger.error(f"[{PLUGIN_NAME}] 转发验证异常: {e}")
            return False, f"转发异常：{e}"

    # ===================== 帮助指令 =====================
    @filter.command("mcverify")
    async def mcverify(self, event: AstrMessageEvent):
        """mcverify：查看本插件说明（验证状态由 MC 服持有，不在本端）"""
        yield event.plain_result(
            "📡 mcverify 转发端说明：\n"
            "· 在绑定群发送「验证 XXXX」即可绑定游戏号\n"
            "· 验证码来自进服被请离时的提示\n"
            "· 验证状态保存在 MC 服（MCMultiLoginCompat），本端只做转发"
        )

    # ===================== 生命周期 =====================
    async def terminate(self):
        if self._session and not self._session.closed:
            await self._session.close()
