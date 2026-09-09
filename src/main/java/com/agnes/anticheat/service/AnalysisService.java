package com.agnes.anticheat.service;

import com.agnes.anticheat.AgnesAntiCheatPlugin;
import com.agnes.anticheat.Settings;
import com.agnes.anticheat.ai.AiClient;
import com.agnes.anticheat.ai.AiVerdict;
import com.agnes.anticheat.data.PlayerDataWindow;
import com.agnes.anticheat.data.SnapshotBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AnalysisService {

    private final AgnesAntiCheatPlugin plugin;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Semaphore limiter = new Semaphore(4);
    private final AtomicInteger failureCount = new AtomicInteger();
    private volatile long pausedUntil;
    private volatile boolean running;
    private int taskId = -1;

    public AnalysisService(AgnesAntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        Settings settings = plugin.settings();
        taskId = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::runWindow,
                settings.intervalSeconds() * 20L,
                settings.intervalSeconds() * 20L
        ).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        running = false;
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void restart() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        running = false;
        failureCount.set(0);
        pausedUntil = 0;
        start();
    }

    private void runWindow() {
        Settings settings = plugin.settings();
        if (settings.apiKey() == null || settings.apiKey().isBlank()) {
            plugin.getLogger().warning("未配置 Agnes API key，AI 分析已暂停。");
            return;
        }
        if (System.currentTimeMillis() < pausedUntil) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.isBypass(player)) {
                continue;
            }
            PlayerDataWindow window = plugin.window(player.getUniqueId(), player.getName());
            String snapshot;
            try {
                snapshot = SnapshotBuilder.build(player, window, settings).toString();
            } catch (Exception e) {
                plugin.getLogger().warning("无法为 " + player.getName() + " 生成分析快照：" + e.getMessage());
                continue;
            }
            window.reset();
            submit(player, snapshot);
        }
    }

    private void submit(Player player, String snapshot) {
        Settings settings = plugin.settings();
        CompletableFuture.runAsync(() -> {
            try {
                limiter.acquire();
                try {
                    AiVerdict verdict = AiClient.analyze(
                            settings.apiKey(),
                            settings.baseUrl(),
                            settings.model(),
                            snapshot,
                            settings.maxTokens(),
                            settings.timeoutSeconds()
                    );
                    failureCount.set(0);
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.alertService().handleVerdict(player, verdict));
                } finally {
                    limiter.release();
                }
            } catch (Exception e) {
                int failures = failureCount.incrementAndGet();
                plugin.getLogger().warning("Agnes AI 分析失败（玩家 " + player.getName() + "）：" + e.getMessage());
                if (failures >= 3) {
                    pausedUntil = System.currentTimeMillis() + plugin.settings().circuitBreakMinutes() * 60_000L;
                    failureCount.set(0);
                    plugin.getLogger().warning("Agnes API 连续失败，熔断 "
                            + plugin.settings().circuitBreakMinutes() + " 分钟。");
                }
            }
        }, executor);
    }

    public String status() {
        Settings settings = plugin.settings();
        long remaining = Math.max(0, pausedUntil - System.currentTimeMillis());
        return "模型=" + settings.model()
                + "，间隔=" + settings.intervalSeconds() + "秒"
                + "，封包模式=" + (plugin.packetMode() ? "已启用" : "降级")
                + "，失败次数=" + failureCount.get()
                + "，熔断=" + (remaining > 0 ? remaining / 1000 + "秒" : "无");
    }
}
