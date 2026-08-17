package net.wifil.mcverify.verify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * mcverify（QQ 绑定验证附属功能）的总开关配置 verifyconfig.json。
 *
 * <p>用户要求：mcverify 项目的<b>所有功能都细化为 true/false 开关</b>。本文件即承载这些开关；
 * 另有少量运行参数（验证码有效期、群广播所需的 QQ 群号、OneBot 地址、验证通道选择）。</p>
 *
 * <p>跨机部署关键（v3 重构）</p>
 * <p>MC 服（Linux）与 AstrBot（Windows）不在同一台机器、文件无法共享，因此：</p>
 * <ul>
 *   <li>验证状态 {@code verify.json} <b>只存在于 MC 服本地</b>（插件数据目录），不再跨机共享。</li>
 *   <li>QQ 群里的「验证 XXXX」由 bot 端转发给 MC 服处理，server 标记后回调 bot 提示成功。</li>
 *   <li>有两种转发通道，由 {@code verifychannel} 选择，<b>两者同构</b>（本插件都是服务器方，
 *     自带 HTTP 入站监听，客户端把群消息 webhook 直推过来，标记后回调）：
 *     <ul>
 *       <li>{@code onebot}：OneBot（NapCat / Lagrange）把群消息 webhook 推给本插件，
 *         标记后直接经 {@code OneBotSender} 回群。</li>
 *       <li>{@code astrbot}：AstrBot 插件（astrbot_plugin_mc_verify）把群消息直连 POST
 *         给本插件（同 OneBot 格式），标记后返回 {@code {"ok":true/false}}，
 *         由 AstrBot 插件根据回调响应回群。</li>
 *       <li>{@code both}：两种都接受。</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>通道与配置项的依赖：两种通道都需要入站端口 {@code verify_webhook_port}；
 * {@code onebot}/{@code both} 另需 {@code onebot_http_url}/{@code onebot_token}（回群用）。
 * {@code astrbottoken} 为历史字段（旧版经 AstrBotAdapter 转发时代保留），直连模式下不再使用。</p>
 *
 * <p>首次运行 / 文件缺失时自动生成默认模板（plugins/MCVerify/verifyconfig.json）。</p>
 */
public final class VerifyConfig {

    /** 验证通道：OneBot 直连（插件自带 HTTP 入站）。 */
    public static final String CHANNEL_ONEBOT = "onebot";
    /** 验证通道：AstrBot 插件直连 POST（与 onebot 同构，本插件作服务器方）。 */
    public static final String CHANNEL_ASTRBOT = "astrbot";
    /** 验证通道：两者都接受。 */
    public static final String CHANNEL_BOTH = "both";

    private static final String DEFAULT_JSON =
            "{\n"
            + "  \"_comment\": {\n"
            + "    \"enabled\": \"mcverify 总开关。false 则完全关闭验证门禁。\",\n"
            + "    \"kick_unverified\": \"未通过验证的玩家进服时踢出。\",\n"
            + "    \"show_code_in_kick\": \"踢出提示里包含验证码，方便玩家拿去群内绑定。\",\n"
            + "    \"welcome_back\": \"已验证玩家进服时发送「欢迎回来」。\",\n"
            + "    \"join_broadcast\": \"群内播报进服。\",\n"
            + "    \"quit_broadcast\": \"群内播报出服。\",\n"
            + "    \"group_verify_listen\": \"群内接收「验证 XXXX」绑定码（onebot 通道由插件 webhook 处理，astrbot 通道由 mcverify 处理）。\",\n"
            + "    \"in_game_join_msg\": \"进服游戏内提示。\",\n"
            + "    \"in_game_quit_msg\": \"出服游戏内提示。\",\n"
            + "    \"code_ttl_seconds\": \"验证码有效期(秒)，过期需重进服获取新码。默认 600。\",\n"
            + "    \"broadcast_group_id\": \"群播报 / 接码所用的 QQ 群号，留空则广播到所有群。\",\n"
            + "    \"verifychannel\": \"验证通道：onebot=OneBot 直连 / astrbot=AstrBot 插件直连(同构) / both=两者都收。本插件均为服务器方，自带 HTTP 入站监听，客户端把群消息 webhook 直推过来。\",\n"
            + "    \"astrbottoken\": \"历史字段：旧版经 AstrBotAdapter 转发时代保留；直连模式下不再使用，留空即可。\",\n"
            + "    \"onebot_http_url\": \"OneBot HTTP 地址（onebot/both 通道用）。\",\n"
            + "    \"onebot_token\": \"OneBot token（onebot/both 通道用）。\",\n"
            + "    \"verify_webhook_port\": \"OneBot 把群消息推到本插件的 HTTP 入站端口（onebot/both 通道用）。\"\n"
            + "  },\n"
            + "  \"enabled\": true,\n"
            + "  \"kick_unverified\": true,\n"
            + "  \"show_code_in_kick\": true,\n"
            + "  \"welcome_back\": true,\n"
            + "  \"join_broadcast\": true,\n"
            + "  \"quit_broadcast\": true,\n"
            + "  \"group_verify_listen\": true,\n"
            + "  \"in_game_join_msg\": true,\n"
            + "  \"in_game_quit_msg\": false,\n"
            + "  \"code_ttl_seconds\": 600,\n"
            + "  \"broadcast_group_id\": \"\",\n"
            + "\n"
            + "  \"verifychannel\": \"astrbot\",\n"
            + "  \"astrbottoken\": \"\",\n"
            + "\n"
            + "  \"onebot_http_url\": \"http://127.0.0.1:3000\",\n"
            + "  \"onebot_token\": \"\",\n"
            + "  \"verify_webhook_port\": 8766\n"
            + "}\n";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final JsonObject root;
    private final File file;

    private VerifyConfig(File file, JsonObject root) {
        this.file = file;
        this.root = root;
    }

    public static VerifyConfig load(File dataFolder, Logger logger) {
        File file = new File(dataFolder, "verifyconfig.json");
        if (!file.exists()) {
            try {
                dataFolder.mkdirs();
                Files.write(file.toPath(), DEFAULT_JSON.getBytes(StandardCharsets.UTF_8));
                if (logger != null) {
                    logger.info("[VerifyConfig] 未找到 verifyconfig.json，已生成默认开关文件："
                            + file.getAbsolutePath());
                }
            } catch (IOException e) {
                if (logger != null) {
                    logger.warning("[VerifyConfig] 生成默认 verifyconfig.json 失败：" + e.getMessage());
                }
            }
        }
        JsonObject parsed = parse(file);
        if (parsed == null) {
            parsed = JsonParser.parseString(DEFAULT_JSON).getAsJsonObject();
        }
        return new VerifyConfig(file, parsed);
    }

    private static JsonObject parse(File file) {
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            if (text.trim().isEmpty()) {
                return null;
            }
            JsonElement el = JsonParser.parseString(text);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 功能开关（true/false）

    public boolean enabled() {
        return root.has("enabled") && root.get("enabled").getAsBoolean();
    }

    /** 未验证时踢出玩家。 */
    public boolean kickUnverified() {
        return bool("kick_unverified", true);
    }

    /** 踢出提示里包含验证码。 */
    public boolean showCodeInKick() {
        return bool("show_code_in_kick", true);
    }

    /** 已验证玩家进服时发送「欢迎回来」。 */
    public boolean welcomeBack() {
        return bool("welcome_back", true);
    }

    /** 群内播报进服（"XXX 进入了服务器"；onebot/both 通道由插件经 OneBot 发送）。 */
    public boolean joinBroadcast() {
        return bool("join_broadcast", true);
    }

    /** 群内播报出服（"XXX 离开了服务器"；onebot/both 通道由插件经 OneBot 发送）。 */
    public boolean quitBroadcast() {
        return bool("quit_broadcast", true);
    }

    /** 群内接收"验证 XXXX"绑定码（onebot 通道由插件 webhook 处理；astrbot 通道由 mcverify 处理）。 */
    public boolean groupVerifyListen() {
        return bool("group_verify_listen", true);
    }

    /** 进服游戏内提示。 */
    public boolean inGameJoinMsg() {
        return bool("in_game_join_msg", true);
    }

    /** 出服游戏内提示。 */
    public boolean inGameQuitMsg() {
        return bool("in_game_quit_msg", false);
    }

    // ------------------------------------------------------------------ 验证通道

    /** 当前验证通道：onebot / astrbot / both（非法值回退 astrbot）。 */
    public String verifyChannel() {
        String c = str("verifychannel", CHANNEL_ASTRBOT).trim().toLowerCase();
        if (c.equals(CHANNEL_ONEBOT) || c.equals(CHANNEL_ASTRBOT) || c.equals(CHANNEL_BOTH)) {
            return c;
        }
        return CHANNEL_ASTRBOT;
    }

    /** 是否走 OneBot 直连接收（onebot / both）。 */
    public boolean useOnebot() {
        String c = verifyChannel();
        return c.equals(CHANNEL_ONEBOT) || c.equals(CHANNEL_BOTH);
    }

    /** 是否走 AstrBot 插件转发（astrbot / both）。 */
    public boolean useAstrbot() {
        String c = verifyChannel();
        return c.equals(CHANNEL_ASTRBOT) || c.equals(CHANNEL_BOTH);
    }

    /** 历史字段：旧版经 AstrBotAdapter 转发时代保留；直连模式下不再使用。 */
    public String astrbotToken() {
        return str("astrbottoken", "");
    }

    // ------------------------------------------------------------------ 运行参数

    public int codeTtlSeconds() {
        return root.has("code_ttl_seconds") ? root.get("code_ttl_seconds").getAsInt() : 600;
    }

    public String broadcastGroupId() {
        return str("broadcast_group_id", "");
    }

    public String onebotHttpUrl() {
        return str("onebot_http_url", "http://127.0.0.1:3000");
    }

    public String onebotToken() {
        return str("onebot_token", "");
    }

    /** OneBot 把群消息推到本插件的 HTTP 入站端口（onebot/both 通道使用）。 */
    public int verifyWebhookPort() {
        int p = root.has("verify_webhook_port") ? root.get("verify_webhook_port").getAsInt() : 8766;
        return (p > 0 && p <= 65535) ? p : 8766;
    }

    private boolean bool(String k, boolean def) {
        return root.has(k) && root.get(k).getAsBoolean();
    }

    private String str(String k, String def) {
        return root.has(k) ? root.get(k).getAsString() : def;
    }

    public void save() throws IOException {
        Files.write(file.toPath(), GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return "VerifyConfig{enabled=" + enabled() + ", kickUnverified=" + kickUnverified()
                + ", welcomeBack=" + welcomeBack() + ", channel=" + verifyChannel()
                + ", joinBroadcast=" + joinBroadcast() + ", quitBroadcast=" + quitBroadcast() + '}';
    }
}
