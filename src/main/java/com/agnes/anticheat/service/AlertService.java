package com.agnes.anticheat.service;

import com.agnes.anticheat.AgnesAntiCheatPlugin;
import com.agnes.anticheat.Settings;
import com.agnes.anticheat.ai.AiVerdict;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AlertService {

    private static final Gson GSON = new Gson();

    private final AgnesAntiCheatPlugin plugin;
    private final Map<UUID, Map<String, Integer>> consecutive = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastWarned = new HashMap<>();

    public AlertService(AgnesAntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    public void handleVerdict(Player player, AiVerdict verdict) {
        if (!player.isOnline()) {
            return;
        }
        Settings settings = plugin.settings();
        if (verdict.suspected()
                && verdict.confidence() >= settings.minConfidence()
                && !verdict.categories().isEmpty()
                && !verdict.reason().isBlank()) {
            boolean alerted = false;
            for (String category : verdict.categories()) {
                int count = consecutive.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                        .merge(category, 1, Integer::sum);
                int required = settings.requiredConsecutive() + (player.getPing() > 300 ? 1 : 0);
                if (count >= required) {
                    long last = lastWarned.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                            .getOrDefault(category, 0L);
                    if (System.currentTimeMillis() - last >= settings.cooldownSeconds() * 1000L) {
                        alert(player, category, verdict);
                        lastWarned.get(player.getUniqueId()).put(category, System.currentTimeMillis());
                        alerted = true;
                    }
                }
            }
            if (!alerted) {
                appendLog(player, verdict, "candidate");
            }
        } else if (!verdict.suspected()) {
            consecutive.remove(player.getUniqueId());
        }
    }

    public void manualWarn(Player player, String category, String reason) {
        if (!player.isOnline()) {
            return;
        }
        alert(player, category, new AiVerdict(true, List.of(category), 1.0, reason));
    }

    private void alert(Player player, String category, AiVerdict verdict) {
        Settings settings = plugin.settings();
        if (settings.warnPlayer()) {
            player.sendMessage("§c[AgnesAC] 检测到可疑行为，请勿使用作弊/复制，管理员将复核。");
        }
        if (settings.alertAdmins()) {
            String message = "§c[AgnesAC] §f" + player.getName()
                    + " §7可疑: §c" + category
                    + " §7置信度: §e" + String.format("%.2f", verdict.confidence())
                    + " §7理由: §f" + verdict.reason();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("agnesanticheat.alerts")) {
                    online.sendMessage(message);
                }
            }
        }
        plugin.getLogger().warning(player.getName() + " 被判定为 " + category + "：" + verdict.reason());
        if (settings.kick()) {
            player.kickPlayer("§c可疑行为已被记录，请联系管理员复核。");
        }
        if (settings.ban()) {
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME)
                    .addBan(player.getName(), "AI anti-cheat verdict", null, "AgnesAntiCheat");
        }
        appendLog(player, verdict, "alert");
    }

    private void appendLog(Player player, AiVerdict verdict, String level) {
        try {
            File logs = new File(plugin.getDataFolder(), "logs");
            Files.createDirectories(logs.toPath());
            File file = new File(logs, LocalDate.now() + ".jsonl");
            JsonObject obj = new JsonObject();
            obj.addProperty("time", System.currentTimeMillis());
            obj.addProperty("level", level);
            obj.addProperty("player", player.getName());
            obj.addProperty("uuid", player.getUniqueId().toString());
            obj.addProperty("suspected", verdict.suspected());
            obj.addProperty("categories", verdict.categories().toString());
            obj.addProperty("confidence", verdict.confidence());
            obj.addProperty("reason", verdict.reason());
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(GSON.toJson(obj) + System.lineSeparator());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("无法写入反作弊日志：" + e.getMessage());
        }
    }
}
