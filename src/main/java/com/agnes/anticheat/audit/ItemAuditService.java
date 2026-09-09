package com.agnes.anticheat.audit;

import com.agnes.anticheat.AgnesAntiCheatPlugin;
import com.agnes.anticheat.Settings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemAuditService implements Listener {

    private static final Gson GSON = new Gson();
    private static final long REPORT_COOLDOWN_MS = 10 * 60_000L;

    private final AgnesAntiCheatPlugin plugin;
    private final Deque<Chunk> chunkQueue = new ArrayDeque<>();
    private final Deque<Chunk> removalQueue = new ArrayDeque<>();
    private final Map<String, Long> reported = new HashMap<>();
    private int scanTaskId = -1;
    private int chunkTaskId = -1;
    private int removalTaskId = -1;

    public ItemAuditService(AgnesAntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (scanTaskId != -1 || !plugin.settings().itemAuditEnabled()) {
            return;
        }
        Settings settings = plugin.settings();
        scanTaskId = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::periodicScan,
                100L,
                settings.itemAuditIntervalSeconds() * 20L
        ).getTaskId();
    }

    public void stop() {
        cancelTask(scanTaskId);
        cancelTask(chunkTaskId);
        cancelTask(removalTaskId);
        scanTaskId = -1;
        chunkTaskId = -1;
        removalTaskId = -1;
        chunkQueue.clear();
        removalQueue.clear();
    }

    public void scanNow() {
        scanPlayers();
        enqueueLoadedChunks();
    }

    public int removeIllegalNow() {
        int removed = 0;
        if (plugin.settings().itemAuditScanPlayers()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                removed += removeIllegalFromInventory(player.getInventory(), player.getName(), "玩家背包");
                removed += removeIllegalFromInventory(player.getEnderChest(), player.getName(), "玩家末影箱");
            }
        }
        enqueueRemovalChunks();
        return removed;
    }

    private void periodicScan() {
        scanPlayers();
        enqueueLoadedChunks();
    }

    private void scanPlayers() {
        if (!plugin.settings().itemAuditScanPlayers()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            scanInventory(player.getInventory(), player.getName(), "玩家背包");
            scanInventory(player.getInventory().getArmorContents(), player.getName(), "玩家装备栏");
            scanInventory(player.getInventory().getExtraContents(), player.getName(), "玩家副手");
            scanInventory(player.getEnderChest(), player.getName(), "玩家末影箱");
        }
    }

    private void enqueueLoadedChunks() {
        if (!plugin.settings().itemAuditScanContainers()) {
            return;
        }
        chunkQueue.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                chunkQueue.add(chunk);
            }
        }
        if (chunkTaskId == -1 && !chunkQueue.isEmpty()) {
            chunkTaskId = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    this::processChunkBatch,
                    1L,
                    1L
            ).getTaskId();
        }
    }

    private void processChunkBatch() {
        int processed = 0;
        int max = plugin.settings().itemAuditMaxChunksPerTick();
        while (processed < max && !chunkQueue.isEmpty()) {
            Chunk chunk = chunkQueue.poll();
            scanChunk(chunk);
            processed++;
        }
        if (chunkQueue.isEmpty()) {
            cancelTask(chunkTaskId);
            chunkTaskId = -1;
        }
    }

    private void enqueueRemovalChunks() {
        if (!plugin.settings().itemAuditScanContainers()) {
            return;
        }
        removalQueue.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                removalQueue.add(chunk);
            }
        }
        if (removalTaskId == -1 && !removalQueue.isEmpty()) {
            removalTaskId = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    this::processRemovalBatch,
                    1L,
                    1L
            ).getTaskId();
        }
    }

    private void processRemovalBatch() {
        int processed = 0;
        int max = plugin.settings().itemAuditMaxChunksPerTick();
        while (processed < max && !removalQueue.isEmpty()) {
            Chunk chunk = removalQueue.poll();
            removeChunk(chunk);
            processed++;
        }
        if (removalQueue.isEmpty()) {
            cancelTask(removalTaskId);
            removalTaskId = -1;
        }
    }

    private void removeChunk(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof InventoryHolder holder) {
                String location = state.getWorld().getName()
                        + " " + state.getX() + "," + state.getY() + "," + state.getZ()
                        + " " + state.getType().name();
                removeIllegalFromInventory(holder.getInventory(), null, location);
            }
        }
    }

    private void scanChunk(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof InventoryHolder holder) {
                String location = state.getWorld().getName()
                        + " " + state.getX() + "," + state.getY() + "," + state.getZ()
                        + " " + state.getType().name();
                scanInventory(holder.getInventory(), null, location);
            }
        }
    }

    private void scanInventory(Inventory inventory, String owner, String location) {
        if (inventory == null) {
            return;
        }
        scanInventory(inventory.getContents(), owner, location);
    }

    private void scanInventory(ItemStack[] items, String owner, String location) {
        if (items == null) {
            return;
        }
        for (ItemStack item : items) {
            reportItem(owner, location, item);
        }
    }

    private int removeIllegalFromInventory(Inventory inventory, String owner, String location) {
        if (inventory == null) {
            return 0;
        }
        int removed = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            List<String> issues = IllegalItemChecker.findIllegal(item);
            if (issues.isEmpty()) {
                continue;
            }
            inventory.setItem(i, null);
            removed++;
            String message = "§c[AgnesAC] §f已销毁违规物品 §7" + item.getType().name()
                    + " §c" + String.join("；", issues)
                    + " §7归属: " + (owner == null ? "容器" : owner)
                    + " §7位置: " + location;
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("agnesanticheat.alerts")) {
                    admin.sendMessage(message);
                }
            }
            plugin.getLogger().warning("已销毁违规物品：" + item.getType().name()
                    + "，归属=" + (owner == null ? "容器" : owner)
                    + "，位置=" + location + "，" + String.join("；", issues));
            appendRemovalLog(owner, location, item, issues);
        }
        return removed;
    }

    private void reportItem(String owner, String location, ItemStack item) {
        List<String> issues = IllegalItemChecker.findIllegal(item);
        if (issues.isEmpty()) {
            return;
        }
        String key = (owner == null ? "container" : owner)
                + "|" + location + "|" + item.getType().name() + "|" + String.join(";", issues);
        long now = System.currentTimeMillis();
        Long last = reported.get(key);
        if (last != null && now - last < REPORT_COOLDOWN_MS) {
            return;
        }
        reported.put(key, now);

        String message = "§c[AgnesAC] §f非法物品 §7" + item.getType().name()
                + " §c" + String.join("；", issues)
                + " §7归属: " + (owner == null ? "容器" : owner)
                + " §7位置: " + location;
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("agnesanticheat.alerts")) {
                admin.sendMessage(message);
            }
        }
        plugin.getLogger().warning("非法物品：" + item.getType().name()
                + "，归属=" + (owner == null ? "容器" : owner)
                + "，位置=" + location + "，" + String.join("；", issues));
        appendAuditLog(owner, location, item, issues);
    }

    private void appendAuditLog(String owner, String location, ItemStack item, List<String> issues) {
        try {
            File dir = new File(plugin.getDataFolder(), "logs");
            Files.createDirectories(dir.toPath());
            File file = new File(dir, "item-audit-" + LocalDate.now() + ".jsonl");
            JsonObject obj = new JsonObject();
            obj.addProperty("time", System.currentTimeMillis());
            obj.addProperty("owner", owner == null ? "container" : owner);
            obj.addProperty("location", location);
            obj.addProperty("item", item.getType().name());
            obj.addProperty("amount", item.getAmount());
            obj.add("issues", GSON.toJsonTree(issues));
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(GSON.toJson(obj) + System.lineSeparator());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("无法写入物品审计日志：" + e.getMessage());
        }
    }

    private void appendRemovalLog(String owner, String location, ItemStack item, List<String> issues) {
        try {
            File dir = new File(plugin.getDataFolder(), "logs");
            Files.createDirectories(dir.toPath());
            File file = new File(dir, "item-removal-" + LocalDate.now() + ".jsonl");
            JsonObject obj = new JsonObject();
            obj.addProperty("time", System.currentTimeMillis());
            obj.addProperty("owner", owner == null ? "container" : owner);
            obj.addProperty("location", location);
            obj.addProperty("item", item.getType().name());
            obj.addProperty("amount", item.getAmount());
            obj.add("issues", GSON.toJsonTree(issues));
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(GSON.toJson(obj) + System.lineSeparator());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("无法写入物品销毁日志：" + e.getMessage());
        }
    }

    private void cancelTask(int taskId) {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (plugin.settings().itemAuditScanContainers()) {
            scanChunk(event.getChunk());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.settings().itemAuditScanPlayers()) {
            Player player = event.getPlayer();
            scanInventory(player.getInventory(), player.getName(), "玩家背包");
            scanInventory(player.getInventory().getArmorContents(), player.getName(), "玩家装备栏");
            scanInventory(player.getInventory().getExtraContents(), player.getName(), "玩家副手");
            scanInventory(player.getEnderChest(), player.getName(), "玩家末影箱");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String owner = event.getWhoClicked() instanceof Player player ? player.getName() : null;
        reportItem(owner, "点击窗口", event.getCurrentItem());
        reportItem(owner, "点击窗口", event.getCursor());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String owner = event.getWhoClicked() instanceof Player player ? player.getName() : null;
        for (ItemStack item : event.getNewItems().values()) {
            reportItem(owner, "拖动窗口", item);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory() != null) {
            String owner = event.getPlayer() instanceof Player player ? player.getName() : null;
            scanInventory(event.getInventory(), owner, "打开的容器");
        }
    }
}
