package com.agnes.anticheat;

import com.agnes.anticheat.command.AdminCommand;
import com.agnes.anticheat.audit.ItemAuditService;
import com.agnes.anticheat.data.PlayerDataWindow;
import com.agnes.anticheat.listener.PacketCollector;
import com.agnes.anticheat.listener.TelemetryListener;
import com.agnes.anticheat.service.AlertService;
import com.agnes.anticheat.service.AnalysisService;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AgnesAntiCheatPlugin extends JavaPlugin {

    private Settings settings;
    private TrustedAdminRegistry trustedAdminRegistry;
    private AlertService alertService;
    private AnalysisService analysisService;
    private PacketCollector packetCollector;
    private PacketListenerCommon packetListenerHandle;
    private ItemAuditService itemAuditService;
    private final Map<UUID, PlayerDataWindow> windows = new ConcurrentHashMap<>();
    private boolean packetMode;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(this);
        trustedAdminRegistry = new TrustedAdminRegistry(this);
        getServer().getOnlinePlayers().forEach(player ->
                trustedAdminRegistry.bind(player.getUniqueId(), player.getName()));
        alertService = new AlertService(this);
        analysisService = new AnalysisService(this);

        if (settings.itemAuditEnabled()) {
            itemAuditService = new ItemAuditService(this);
            getServer().getPluginManager().registerEvents(itemAuditService, this);
            itemAuditService.start();
        }

        getServer().getPluginManager().registerEvents(new TelemetryListener(this), this);

        if (settings.packetEnabled() && getServer().getPluginManager().getPlugin("packetevents") != null) {
            try {
                packetCollector = new PacketCollector(this);
                packetListenerHandle = packetCollector.register();
                packetMode = true;
                getLogger().info("PacketEvents 封包采集已启用。");
            } catch (Throwable e) {
                getLogger().warning("检测到 PacketEvents，但启用失败：" + e.getMessage());
            }
        } else {
            getLogger().info("未检测到 PacketEvents，仅使用 Bukkit 事件采集。");
        }

        AdminCommand adminCommand = new AdminCommand(this);
        getCommand("ac").setExecutor(adminCommand);
        getCommand("ac").setTabCompleter(adminCommand);

        analysisService.start();
        getLogger().info("AgnesAntiCheat 已启用。");
    }

    @Override
    public void onDisable() {
        if (analysisService != null) {
            analysisService.stop();
        }
        if (itemAuditService != null) {
            itemAuditService.stop();
        }
        if (packetCollector != null && packetListenerHandle != null) {
            try {
                packetCollector.unregister(packetListenerHandle);
            } catch (Throwable ignored) {
            }
        }
        windows.clear();
        getLogger().info("AgnesAntiCheat 已禁用。");
    }

    public PlayerDataWindow window(UUID uuid, String name) {
        return windows.computeIfAbsent(uuid, ignored -> new PlayerDataWindow(uuid, name));
    }

    public void removeWindow(UUID uuid) {
        windows.remove(uuid);
    }

    public boolean isBypass(Player player) {
        return player.isOp() && trustedAdminRegistry.isTrusted(player.getUniqueId(), player.getName());
    }

    public Settings settings() {
        return settings;
    }

    public TrustedAdminRegistry trustedRegistry() {
        return trustedAdminRegistry;
    }

    public AlertService alertService() {
        return alertService;
    }

    public AnalysisService analysisService() {
        return analysisService;
    }

    public ItemAuditService itemAuditService() {
        return itemAuditService;
    }

    public boolean packetMode() {
        return packetMode;
    }
}
