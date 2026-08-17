# MCVerify

> Bukkit / Spigot / Paper / Purpur 服务端的**独立 QQ 群绑定验证门禁**插件。

## 项目简介

MCVerify 是一个**只做「进服验证码 + QQ 群放行」门禁**的 Bukkit 插件，
与登录兼容（MultiLogin）完全解耦——它不接管 `hasJoinedServer`、不碰正版/皮肤站逻辑。

工作流程：

1. 未验证玩家进服 → 服务端生成一段验证码并踢出，提示里带上验证码。
2. 玩家去指定 QQ 群发送 `验证 XXXX`。
3. 验证通过后，下次进服直接放行。

它有两种接码通道（见下方「验证通道」），**不依赖任何外部 HTTP 验证服务**。

> 如果你要的是「多账户正版登录兼容（LittleSkin / 自建皮肤站 + 正版同服）」，
> 请去 [`mc-multilogin-compat`](https://github.com/ssc2991lyh/mc-multilogin-compat)（纯版）
> 或 [`mc-multilogin-verify-plugin`](https://github.com/ssc2991lyh/mc-multilogin-verify-plugin)（联合版）。

## 三种部署形态

| 仓库 | 内容 | 是否含 mcverify |
| --- | --- | --- |
| `mc-multilogin-compat` | 纯登录兼容（多账户正版） | ❌ 不含 |
| `mc-multilogin-verify-plugin` | 登录兼容 + 内置 mcverify 转发 | ✅ 内置 |
| `mc-verify-plugin`（本仓库） | **仅** mcverify 门禁，独立 JAR | ✅ 仅此 |

本仓库的 JAR 与联合版**二选一**安装即可，二者共用同一份 `verify.json` /
`verifyconfig.json` 约定；不要两个同时装（会重复接管登录事件）。

## 验证通道

`verifyconfig.json` 里的 `verifychannel` 决定群消息怎么进来：

- `astrbot`（默认）：mcverify（AstrBot 插件，由你另行按官方规范构建）收到群内
  `验证 XXXX` 后，经 **AstrBotAdapter 的 `command/execute`** 在 MC 服执行
  `/mcverify verify <code>`，由本插件标记并回调 bot 提示成功。
  - 需要 `astrbottoken`（从 MC 服 `plugins/AstrbotAdapter/config.yml` 复制）。
- `onebot`：本插件自带 HTTP 入站监听，OneBot 把群消息 webhook 直接推过来，
  标记后直接经 OneBot 回群。
  - 需要 `onebot_http_url` / `onebot_token` / `verify_webhook_port`。
- `both`：两种都收。

MC 服（Linux）与 AstrBot（Windows）常不在同一台机器，因此验证状态 `verify.json`
**只存在于 MC 服本地**（插件数据目录），不跨机共享——这正好是 `astrbot` 通道的设计意图。

## 配置

首次运行自动生成 `plugins/MCVerify/verifyconfig.json`，字段如下（文件内也带 `_comment` 说明）：

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `enabled` | bool | `true` | mcverify 总开关。 |
| `kick_unverified` | bool | `true` | 未通过验证的玩家进服时踢出。 |
| `show_code_in_kick` | bool | `true` | 踢出提示里包含验证码，方便玩家拿去群内绑定。 |
| `welcome_back` | bool | `true` | 已验证玩家进服时发送「欢迎回来」。 |
| `join_broadcast` | bool | `true` | 群内播报进服。 |
| `quit_broadcast` | bool | `true` | 群内播报出服。 |
| `group_verify_listen` | bool | `true` | 群内接收「验证 XXXX」绑定码。 |
| `in_game_join_msg` | bool | `true` | 进服游戏内提示。 |
| `in_game_quit_msg` | bool | `false` | 出服游戏内提示。 |
| `code_ttl_seconds` | int | `600` | 验证码有效期（秒），过期需重进服获取新码。 |
| `broadcast_group_id` | string | `""` | 群播报 / 接码所用的 QQ 群号，留空则广播到所有群。 |
| `verifychannel` | string | `"astrbot"` | 验证通道：`onebot` / `astrbot` / `both`。 |
| `astrbottoken` | string | `""` | AstrBotAdapter 通信 token（`astrbot`/`both` 通道用）。 |
| `onebot_http_url` | string | `http://127.0.0.1:3000` | OneBot HTTP 地址（`onebot`/`both` 通道用）。 |
| `onebot_token` | string | `""` | OneBot token（`onebot`/`both` 通道用）。 |
| `verify_webhook_port` | int | `8766` | OneBot 把群消息推到本插件的 HTTP 入站端口（`onebot`/`both` 通道用）。 |

> 文件里所有 `_comment` 开头的键都是说明，加载器会忽略，不影响逻辑。

## 指令

`/mcverify <verify|status|reload>`（权限 `mcverify.admin`，默认 OP）

- `verify <code>`：按验证码标记某玩家已验证（通常由 AstrBot 插件 / OneBot 自动调用，也可手动）。
- `status`：查看门禁状态、验证通道与已配置项。
- `reload`：热重载 `verifyconfig.json`（不影响已在线的验证逻辑）。

## 构建

```bash
# 需要 JDK 25（paper-api 26.1.2 依赖要求；产物目标字节码 Java 8，适配 1.8 ~ 最新 Purpur）
./gradlew build
# 产物：build/libs/mc-verify-bukkit-<version>.jar
```

将 JAR 放入服务端 `plugins/` 重启即可。首次运行自动生成 `verifyconfig.json`
（与 `verify.json` 验证状态文件）于 `plugins/MCVerify/`。

## 开源协议

本项目以 **GNU Affero General Public License v3.0 (AGPL-3.0)** 发布，详见
[LICENSE](./LICENSE)。
