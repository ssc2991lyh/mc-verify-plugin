# astrbot_plugin_mc_verify · Minecraft QQ 群验证码白名单门禁（转发端）

> AstrBot 插件：监听群消息「验证 XXXX」→ 转发到 MC 服标记已验证。

## 它是什么 / 不是什么

这是 **v3 转发重构版**。角色定位：

- **MC 服（`MCMultiLoginCompat` Java 插件，Linux）是「门禁权威方 + 验证状态持有方」**：
  玩家进服时生成验证码、写入 MC 服本地的 `verify.json`、未验证则 `/kick`，
  并提供「按验证码标记已验证」的入口（`/multilogin verify <code>`）。
- **本插件（AstrBot 端，Windows）是「QQ 群验证码转发端」**：
  监听群消息「验证 XXXX」→ 经 **AstrBotAdapter** 的 REST `command/execute`
  把 `/multilogin verify <code>` 发到 MC 服执行 → 收到成功回调 → 回群「验证成功」。

⚠️ 本插件**不写任何 json、不轮询、不冻结**——所有状态都在 MC 服本地。
（跨机无法共享文件，故只做「转发 + 收回调」。）

> 与之配合的 MC 服门禁插件：`MCMultiLoginCompat`
> （联合版 [`mc-multilogin-verify-plugin`](https://github.com/ssc2991lyh/mc-multilogin-verify-plugin)
> 或纯版 `mc-multilogin-compat` 都可提供 `/multilogin verify` 入口）。

## 前置依赖

1. MC 服已装 **AstrBotAdapter**（MC 插件，`AstrbotAdaptor-*.jar`）并正常运行（已生成 token）。
2. MC 服已装 **MCMultiLoginCompat**（提供 `/multilogin verify` 指令与门禁逻辑）。
3. AstrBot 已通过 AstrBotAdapter 把 MC 服与你的 QQ 群绑定。

## 安装

把本文件夹复制到 AstrBot 的 `data/plugins/astrbot_plugin_mc_verify/`，
在 WebUI 插件管理里**重载插件**（或重启 AstrBot）。

> 依赖：`aiohttp>=3.9.0`（AstrBot 一般已自带；若缺失则 `pip install aiohttp`）。

## 配置（WebUI 插件配置面板）

| 项 | 说明 | 默认 |
| --- | --- | --- |
| `mc_host` | AstrBotAdapter（MC 插件）所在主机；同机 `127.0.0.1`，否则填 MC 服 IP | `127.0.0.1` |
| `mc_rest_port` | AstrBotAdapter 的 REST / WS 共用端口 | `8765` |
| `astrbottoken` | AstrBotAdapter 通信 Token（从 `plugins/AstrbotAdapter/config.yml` 复制） | 空 |
| `group_id` | 监听「验证 XXXX」的 QQ 群号（填与 MC 服绑定的群；留空=所有群） | 空 |
| `group_verify_listen` | 是否启用群接码（`false` 则完全不处理「验证 XXXX」） | `true` |

## 使用流程

1. 玩家进服（MC 服侧）→ 未验证被 `/kick`，踢出提示含「验证 XXXXXX」。
2. 玩家在配置好的 QQ 群发送：`验证 XXXXXX`（大小写不限）。
3. 本插件转发到 MC 服执行 `/multilogin verify XXXXXX`：
   - 成功 → 群内回「✅ 验证成功！已绑定 QQ，可以重新进服啦~」
   - 失败 → 群内回「❌ 验证失败：<原因>（过期请重进服获取新码）」
4. 玩家重进服：MC 服查 `verify.json` 已验证 → 直接放行。

## 管理指令（群里 / 私聊）

- `/mcverify` —— 查看本插件说明（验证状态由 MC 服持有，不在本端）。

## 注意事项

- 本插件**不需要 RCON**，走 AstrBotAdapter 的 REST。
- 验证码由 MC 服生成与校验；本端只负责把群消息转成 MC 服指令。
- 跨机部署时确保 `mc_host:mc_rest_port` 与 AstrBotAdapter 一致、`astrbottoken` 正确。

## 开源协议

本项目以 **GNU Affero General Public License v3.0 (AGPL-3.0)** 发布，详见
[LICENSE](./LICENSE)。配套的 MC 服门禁插件 `MCMultiLoginCompat` 同样以 AGPL-3.0 发布。
