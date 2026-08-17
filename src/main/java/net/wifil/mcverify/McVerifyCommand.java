package net.wifil.mcverify;

import net.wifil.mcverify.verify.VerifyConfig;
import net.wifil.mcverify.verify.VerifyState;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code /mcverify <status|reload|verify>} 的实现（MCVerify 独立版）。
 *
 * <p>其中 {@code /mcverify verify <验证码>} 是 AstrBot 验证通道的 server 端入口：
 * mcverify(AstrBot 插件) 收到 QQ 群「验证 XXXX」后，经 AstrBotAdapter REST
 * {@code command/execute} 以控制台身份执行本指令，由 MC 服在本地 verify.json 按码标记已验证。
 * 指令输出里的「验证成功」字样会被 mcverify 当作回调成功标志回群提示。</p>
 */
public class McVerifyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = new ArrayList<String>();

    static {
        SUB.add("status");
        SUB.add("reload");
        SUB.add("verify");
    }

    private final McVerifyPlugin plugin;

    public McVerifyCommand(McVerifyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = (args.length == 0) ? "status" : args[0].toLowerCase();

        if ("reload".equals(sub)) {
            plugin.reload();
            sender.sendMessage(ChatColor.GREEN + "[MCVerify] 配置已重载。"
                    + ChatColor.GRAY + "（门禁监听不变，如需重新挂钩请重启服务器）");
            return true;
        }

        if ("status".equals(sub)) {
            sendStatus(sender);
            return true;
        }

        if ("verify".equals(sub)) {
            handleVerify(sender, args);
            return true;
        }

        sender.sendMessage(ChatColor.RED + "用法: /" + label + " <status|reload|verify>");
        return true;
    }

    /**
     * 处理 {@code /mcverify verify <验证码>}。
     */
    private void handleVerify(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /mcverify verify <验证码>");
            return;
        }
        String code = args[1].trim().toUpperCase();
        VerifyState state = plugin.verifyState();
        if (state == null) {
            sender.sendMessage(ChatColor.RED + "[MCVerify] 验证模块未初始化。");
            return;
        }
        String name = state.markVerifiedByCode(code, "");
        if (name != null) {
            sender.sendMessage(ChatColor.GREEN + "[MCVerify] 验证成功：玩家 " + ChatColor.YELLOW + name
                    + ChatColor.GREEN + " 已绑定，可重新进服。");
        } else {
            sender.sendMessage(ChatColor.RED + "[MCVerify] 无效或已过期的验证码：" + code);
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "===== MCVerify 状态 =====");
        sender.sendMessage(ChatColor.GRAY + "版本: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        VerifyConfig cfg = plugin.verifyConfig();
        sender.sendMessage(ChatColor.GRAY + "总开关: " + flag(cfg.enabled()));
        sender.sendMessage(ChatColor.GRAY + "未验证踢出: " + flag(cfg.kickUnverified()));
        sender.sendMessage(ChatColor.GRAY + "欢迎回来: " + flag(cfg.welcomeBack()));
        sender.sendMessage(ChatColor.GRAY + "验证通道: " + ChatColor.WHITE + cfg.verifyChannel());
        sender.sendMessage(ChatColor.GRAY + "群进服播报: " + flag(cfg.joinBroadcast()));
        sender.sendMessage(ChatColor.GRAY + "群出服播报: " + flag(cfg.quitBroadcast()));
    }

    private static String flag(boolean b) {
        return (b ? ChatColor.GREEN + "开" : ChatColor.RED + "关").toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<String>();
            for (String s : SUB) {
                if (s.startsWith(prefix)) {
                    out.add(s);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
