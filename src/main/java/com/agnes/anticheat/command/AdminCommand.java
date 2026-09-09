package com.agnes.anticheat.command;

import com.agnes.anticheat.AgnesAntiCheatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AgnesAntiCheatPlugin plugin;

    public AdminCommand(AgnesAntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("agnesanticheat.admin")) {
            sender.sendMessage("§c你没有权限使用 AgnesAntiCheat 指令。");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§7用法: /ac reload | status | scanitems | removeillegal | warn <玩家名> [类别] | trust add <名字> | trust remove <名字>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.settings().reload();
                plugin.trustedRegistry().load();
                plugin.analysisService().restart();
                sender.sendMessage("§aAgnesAntiCheat 配置已重载。");
                return true;
            }
            case "status" -> {
                sender.sendMessage("§7AgnesAntiCheat: " + plugin.analysisService().status());
                sender.sendMessage("§7在线玩家: §f" + Bukkit.getOnlinePlayers().size()
                        + " §7| 信任管理员: §f" + plugin.trustedRegistry().names().size());
                return true;
            }
            case "scanitems" -> {
                if (plugin.itemAuditService() == null) {
                    sender.sendMessage("§c物品审计未启用。");
                    return true;
                }
                plugin.itemAuditService().scanNow();
                sender.sendMessage("§a已开始扫描玩家背包和已加载区块容器。");
                return true;
            }
            case "removeillegal" -> {
                if (!isTrustedAdmin(sender)) {
                    sender.sendMessage("§c只有已注册的信任管理员才能销毁物品。");
                    return true;
                }
                if (plugin.itemAuditService() == null) {
                    sender.sendMessage("§c物品审计未启用。");
                    return true;
                }
                int removed = plugin.itemAuditService().removeIllegalNow();
                sender.sendMessage("§a已销毁玩家背包中的 " + removed + " 个违规物品，容器扫描销毁已开始。");
                return true;
            }
            case "warn" -> {
                return warn(sender, args);
            }
            case "trust" -> {
                return trust(sender, args);
            }
            default -> sender.sendMessage("§c未知子命令: " + args[0]);
        }
        return true;
    }

    private boolean warn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§7用法: /ac warn <玩家名> [类别]");
            return true;
        }
        if (!isTrustedAdmin(sender)) {
            sender.sendMessage("§c只有已注册的信任管理员才能手动警告。");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§c玩家不在线: " + args[1]);
            return true;
        }
        String category = args.length > 2 ? args[2].toLowerCase() : "damage";
        plugin.alertService().manualWarn(
                target,
                category,
                "管理员手动测试：模拟" + category + "违规"
        );
        sender.sendMessage("§a已向 " + target.getName() + " 发送 " + category + " 警告，并通知管理员。");
        return true;
    }

    private boolean isTrustedAdmin(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        return sender instanceof Player player && plugin.isBypass(player);
    }

    private boolean trust(CommandSender sender, String[] args) {
        boolean console = sender instanceof ConsoleCommandSender;
        boolean allowed = console || plugin.settings().allowInGameTrust();
        if (!allowed) {
            sender.sendMessage("§c游戏内信任管理默认关闭，请使用控制台或修改 trust.allow-in-game-command。");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§7用法: /ac trust add <名字> | /ac trust remove <名字>");
            return true;
        }
        String name = args[2];
        if (args[1].equalsIgnoreCase("add")) {
            boolean added = plugin.trustedRegistry().add(name);
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                plugin.trustedRegistry().bind(online.getUniqueId(), online.getName());
            }
            sender.sendMessage(added ? "§a已注册信任管理员: " + name : "§e该名字已在信任名单中。");
            return true;
        }
        if (args[1].equalsIgnoreCase("remove")) {
            boolean removed = plugin.trustedRegistry().remove(name);
            sender.sendMessage(removed ? "§a已移除信任管理员: " + name : "§e该名字不在信任名单中。");
            return true;
        }
        sender.sendMessage("§7用法: /ac trust add <名字> | /ac trust remove <名字>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("agnesanticheat.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("reload", "status", "scanitems", "removeillegal", "warn", "trust");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("warn")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("warn")) {
            return List.of("damage", "speed", "fly", "killaura", "reach", "duplication");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("trust")) {
            return List.of("add", "remove");
        }
        return List.of();
    }
}
