package net.wifil.mcverify;

import net.wifil.mcverify.verify.OneBotSender;
import net.wifil.mcverify.verify.VerifyCallbackServer;
import net.wifil.mcverify.verify.VerifyConfig;
import net.wifil.mcverify.verify.VerifyGate;
import net.wifil.mcverify.verify.VerifyState;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * MCVerify —— 独立的 QQ 群绑定验证门禁 Bukkit 插件。
 *
 * <p>从 mc-multilogin 联合版中把「mcverify 门禁」这一块单独抽出来，做成<b>不依赖</b>
 * 多账户登录兼容（hasJoined 接管）的独立插件。它只负责：</p>
 * <ul>
 *   <li>玩家进服时查验证状态：已验证放行并问候，未验证生成验证码并踢出；</li>
 *   <li>提供 {@code /mcverify verify <code>} 指令（手动按码标记，或调试用），按码标记已验证；</li>
 *   <li>入站直连通道（onebot / astrbot 同构）：自带 HTTP 入站监听接收群「验证 XXXX」，
 *       按码标记后回调——OneBot 直连经 {@code OneBotSender} 回群，AstrBot 插件
 *       （astrbot_plugin_mc_verify）直连则返回 {@code {"ok":true/false}} 由插件回群。</li>
 * </ul>
 *
 * <p>与联合版 {@code mc-multilogin-verify-plugin} 共享同一份 {@code verify.json} /
 * {@code verifyconfig.json} 约定，因此两个插件<b>二选一</b>安装即可，不要同时装。</p>
 */
public class McVerifyPlugin extends JavaPlugin {

    private VerifyConfig verifyConfig;
    private VerifyState verifyState;
    private OneBotSender onebot;
    private VerifyGate verifyGate;
    private VerifyCallbackServer callbackServer;
    private boolean verifyRegistered;

    @Override
    public void onEnable() {
        // 首次运行自动生成 verifyconfig.json（缺失即自举）
        this.verifyConfig = VerifyConfig.load(getDataFolder(), getLogger());
        this.verifyState = new VerifyState(getDataFolder(), "verify.json", getLogger());
        this.verifyState.setTtlSeconds(verifyConfig.codeTtlSeconds());

        initVerifyChannel();

        if (!verifyRegistered) {
            this.verifyGate = new VerifyGate(this);
            getServer().getPluginManager().registerEvents(verifyGate, this);
            verifyRegistered = true;
        }

        if (getCommand("mcverify") != null) {
            getCommand("mcverify").setExecutor(new McVerifyCommand(this));
        }

        getLogger().info("[MCVerify] 门禁已加载，开关：enabled=" + verifyConfig.enabled()
                + " kick_unverified=" + verifyConfig.kickUnverified()
                + " channel=" + verifyConfig.verifyChannel());
    }

    @Override
    public void onDisable() {
        if (this.callbackServer != null) {
            this.callbackServer.stop();
            this.callbackServer = null;
        }
        this.onebot = null;
        getLogger().info("[MCVerify] 已卸载。");
    }

    /**
     * 初始化验证通道（onebot / astrbot / both）。
     * 热重载时先停掉旧入站监听，避免端口占用。
     *
     * <p>两种通道同构：本插件都是「服务器方」——自带 HTTP 入站监听
     * （{@link VerifyCallbackServer}），客户端（OneBot 或 astrbot_plugin_mc_verify）
     * 把群消息 webhook 直推过来，本插件按码标记后返回回调结果：
     * <ul>
     *   <li>onebot：标记后经 {@link OneBotSender} 直接回群；</li>
     *   <li>astrbot：标记后返回 {@code {"ok":true/false}}，由 AstrBot 插件
     *       （astrbot_plugin_mc_verify）根据回调响应回群。</li>
     * </ul></p>
     */
    private void initVerifyChannel() {
        if (this.callbackServer != null) {
            this.callbackServer.stop();
            this.callbackServer = null;
        }
        boolean useOnebot = verifyConfig.useOnebot();
        boolean useAstrbot = verifyConfig.useAstrbot();

        if (useOnebot) {
            this.onebot = new OneBotSender(verifyConfig.onebotHttpUrl(), verifyConfig.onebotToken(),
                    getLogger(), 10);
        } else {
            this.onebot = null;
        }

        // onebot / astrbot / both 都启动入站监听（mcverify 作为服务器方接受连接）
        if (useOnebot || useAstrbot) {
            this.callbackServer = new VerifyCallbackServer(this);
            this.callbackServer.start(verifyConfig.verifyWebhookPort());
        }
    }

    /** 热重载配置（verifyconfig.json + verify.json 状态重建；监听器不重复注册）。 */
    public void reload() {
        this.verifyConfig = VerifyConfig.load(getDataFolder(), getLogger());
        this.verifyState = new VerifyState(getDataFolder(), "verify.json", getLogger());
        this.verifyState.setTtlSeconds(verifyConfig.codeTtlSeconds());
        initVerifyChannel();
        getLogger().info("[MCVerify] 配置已重载：" + verifyConfig);
    }

    // ------------------------------------------------------------------
    // 对外访问（供 verify 包在任意线程调用）
    // ------------------------------------------------------------------

    /** mcverify 门禁开关配置（verifyconfig.json）。 */
    public VerifyConfig verifyConfig() {
        return verifyConfig;
    }

    /** 共享验证状态（verify.json，与 AstrBot 插件 mcverify 共用）。 */
    public VerifyState verifyState() {
        return verifyState;
    }

    /** OneBot 群消息发送器（onebot/both 通道）。 */
    public OneBotSender onebot() {
        return onebot;
    }
}
