package com.agnes.anticheat;

import org.bukkit.configuration.file.YamlConfiguration;

public class Settings {

    private final AgnesAntiCheatPlugin plugin;

    private String baseUrl;
    private String model;
    private String apiKey;
    private int maxTokens;
    private int timeoutSeconds;
    private int circuitBreakMinutes;
    private int intervalSeconds;
    private double minConfidence;
    private int requiredConsecutive;
    private int cooldownSeconds;
    private double damageTolerance;
    private double speedTolerance;
    private boolean packetEnabled;
    private boolean logCommands;
    private boolean logChatContent;
    private boolean itemAuditEnabled;
    private int itemAuditIntervalSeconds;
    private int itemAuditMaxChunksPerTick;
    private boolean itemAuditScanPlayers;
    private boolean itemAuditScanContainers;
    private boolean warnPlayer;
    private boolean alertAdmins;
    private boolean kick;
    private boolean ban;
    private boolean allowInGameTrust;

    public Settings(AgnesAntiCheatPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        YamlConfiguration c = (YamlConfiguration) plugin.getConfig();
        baseUrl = c.getString("api.base-url", "https://api.agnes-ai.cn");
        model = c.getString("api.model", "agnes-2.5-flash");
        apiKey = c.getString("api.api-key", "");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("AGNES_API_KEY");
        }
        maxTokens = c.getInt("api.max-tokens", 1200);
        timeoutSeconds = c.getInt("api.timeout-seconds", 60);
        circuitBreakMinutes = c.getInt("api.circuit-break-minutes", 5);
        intervalSeconds = Math.max(10, c.getInt("analysis.interval-seconds", 60));
        minConfidence = c.getDouble("analysis.min-confidence", 0.85);
        requiredConsecutive = Math.max(1, c.getInt("analysis.required-consecutive", 2));
        cooldownSeconds = c.getInt("analysis.cooldown-seconds", 120);
        damageTolerance = c.getDouble("calculation.damage-tolerance", 0.15);
        speedTolerance = c.getDouble("calculation.speed-tolerance", 0.25);
        packetEnabled = c.getBoolean("packet.enabled", true);
        logCommands = c.getBoolean("interaction.log-commands", true);
        logChatContent = c.getBoolean("interaction.log-chat-content", false);
        itemAuditEnabled = c.getBoolean("item-audit.enabled", true);
        itemAuditIntervalSeconds = Math.max(30, c.getInt("item-audit.interval-seconds", 120));
        itemAuditMaxChunksPerTick = Math.max(1, c.getInt("item-audit.max-chunks-per-tick", 64));
        itemAuditScanPlayers = c.getBoolean("item-audit.scan-player-inventories", true);
        itemAuditScanContainers = c.getBoolean("item-audit.scan-loaded-containers", true);
        warnPlayer = c.getBoolean("actions.warn-player", true);
        alertAdmins = c.getBoolean("actions.alert-admins", true);
        kick = c.getBoolean("actions.kick", false);
        ban = c.getBoolean("actions.ban", false);
        allowInGameTrust = c.getBoolean("trust.allow-in-game-command", false);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public String apiKey() {
        return apiKey;
    }

    public int maxTokens() {
        return maxTokens;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    public int circuitBreakMinutes() {
        return circuitBreakMinutes;
    }

    public int intervalSeconds() {
        return intervalSeconds;
    }

    public double minConfidence() {
        return minConfidence;
    }

    public int requiredConsecutive() {
        return requiredConsecutive;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public double damageTolerance() {
        return damageTolerance;
    }

    public double speedTolerance() {
        return speedTolerance;
    }

    public boolean packetEnabled() {
        return packetEnabled;
    }

    public boolean logCommands() {
        return logCommands;
    }

    public boolean logChatContent() {
        return logChatContent;
    }

    public boolean itemAuditEnabled() {
        return itemAuditEnabled;
    }

    public int itemAuditIntervalSeconds() {
        return itemAuditIntervalSeconds;
    }

    public int itemAuditMaxChunksPerTick() {
        return itemAuditMaxChunksPerTick;
    }

    public boolean itemAuditScanPlayers() {
        return itemAuditScanPlayers;
    }

    public boolean itemAuditScanContainers() {
        return itemAuditScanContainers;
    }

    public boolean warnPlayer() {
        return warnPlayer;
    }

    public boolean alertAdmins() {
        return alertAdmins;
    }

    public boolean kick() {
        return kick;
    }

    public boolean ban() {
        return ban;
    }

    public boolean allowInGameTrust() {
        return allowInGameTrust;
    }
}
