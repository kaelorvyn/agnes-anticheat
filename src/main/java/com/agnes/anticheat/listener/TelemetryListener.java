package com.agnes.anticheat.listener;

import com.agnes.anticheat.AgnesAntiCheatPlugin;
import com.agnes.anticheat.data.PlayerDataWindow;
import com.agnes.anticheat.calc.DamageCalculator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Bee;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TelemetryListener implements Listener {

    private final AgnesAntiCheatPlugin plugin;
    private final Map<UUID, LastMove> lastMoves = new HashMap<>();

    public TelemetryListener(AgnesAntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.window(player.getUniqueId(), player.getName());
        plugin.trustedRegistry().bind(player.getUniqueId(), player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.removeWindow(event.getPlayer().getUniqueId());
        lastMoves.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerDataWindow w = plugin.window(player.getUniqueId(), player.getName());
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from == null || to == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        if (dx * dx + dy * dy + dz * dz > 100) {
            w.recordTeleport();
            return;
        }

        long now = System.currentTimeMillis();
        LastMove last = lastMoves.get(player.getUniqueId());
        if (last == null) {
            lastMoves.put(player.getUniqueId(), new LastMove(to, now));
            return;
        }
        long elapsedMs = now - last.time;
        if (elapsedMs < 40) {
            return;
        }
        long maxInterval = player.getPing() > 300 ? 500 : 250;
        double dt = Math.max(0.04, elapsedMs / 1000.0);
        double measuredDx = to.getX() - last.location.getX();
        double measuredDy = to.getY() - last.location.getY();
        double measuredDz = to.getZ() - last.location.getZ();
        double distance = Math.hypot(measuredDx, measuredDz);
        if (elapsedMs > maxInterval || distance > 5) {
            lastMoves.put(player.getUniqueId(), new LastMove(to, now));
            return;
        }
        double horizontal = distance / dt;
        double vertical = measuredDy / dt;
        boolean jumping = !to.getBlock().isSolid() && dy > 0;
        boolean soulSand = isSoulSand(to);
        w.recordMove(
                horizontal,
                vertical,
                to.getBlock().isSolid() || player.isOnGround(),
                jumping,
                player.isFlying(),
                player.isGliding(),
                player.isInsideVehicle(),
                vehicleSpeed(player),
                player.isSwimming(),
                soulSand
        );
        lastMoves.put(player.getUniqueId(), new LastMove(to, now));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        PlayerDataWindow w = plugin.window(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        w.sawFlight = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        plugin.window(event.getPlayer().getUniqueId(), event.getPlayer().getName()).recordTeleport();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        PlayerDataWindow w = plugin.window(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        w.gamemodeChanges.add(event.getNewGameMode().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOutgoingDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Player player = null;
        if (damager instanceof Player p) {
            player = p;
        } else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            player = p;
        }
        if (player == null) {
            return;
        }
        PlayerDataWindow w = plugin.window(player.getUniqueId(), player.getName());
        double distance = player.getLocation().distance(event.getEntity().getLocation());
        w.recordAttack(distance, (int) event.getDamage());
        w.distinctTargets = w.targetIds.size();
        w.targetIds.add(event.getEntity().getUniqueId());
        w.distinctTargets = w.targetIds.size();

        double actualRaw = safeBaseDamage(event);
        double expected = expectedOutgoingDamage(player, event.getEntity());
        w.recordOutgoingDamage(actualRaw, expected);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIncomingDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerDataWindow w = plugin.window(player.getUniqueId(), player.getName());
        double raw = safeBaseDamage(event);
        double expected = expectedIncomingDamage(player, raw);
        w.recordIncomingDamage(raw, expected, event.getFinalDamage());
        w.maxNoDamageTicks = Math.max(w.maxNoDamageTicks, player.getNoDamageTicks());
        w.maxFireTicks = Math.max(w.maxFireTicks, player.getFireTicks());
        w.maxFallDistance = Math.max(w.maxFallDistance, (int) player.getFallDistance());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PlayerDataWindow w = plugin.window(player.getUniqueId(), player.getName());
        w.clickCount++;
        delta(w, event.getCurrentItem(), -1);
        delta(w, event.getCursor(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PlayerDataWindow w = plugin.window(player.getUniqueId(), player.getName());
        w.dragCount++;
        event.getNewItems().forEach((slot, item) -> delta(w, item, 1));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PlayerDataWindow w = plugin.window(player.getUniqueId(), player.getName());
        w.creativeActions++;
        delta(w, event.getCurrentItem(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            plugin.window(player.getUniqueId(), player.getName()).containerOpens++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerDataWindow w = plugin.window(player.getUniqueId(), player.getName());
            w.itemPickups++;
            delta(w, event.getItem().getItemStack(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        PlayerDataWindow w = plugin.window(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        w.itemDrops++;
        delta(w, event.getItemDrop().getItemStack(), -1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            PlayerDataWindow w = plugin.window(player.getUniqueId(), player.getName());
            w.craftCount++;
            if (event.getCurrentItem() != null) {
                delta(w, event.getCurrentItem(), 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        PlayerDataWindow w = plugin.window(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        w.furnaceExtractCount += event.getItemAmount();
        w.recordItemDelta(event.getItemType().name(), event.getItemAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        PlayerDataWindow w = plugin.window(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        w.blockBreaks++;
        w.maxBlockDistance = Math.max(w.maxBlockDistance, event.getPlayer().getLocation().distance(event.getBlock().getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        PlayerDataWindow w = plugin.window(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        w.blockPlaces++;
        w.maxBlockDistance = Math.max(w.maxBlockDistance, event.getPlayer().getLocation().distance(event.getBlockPlaced().getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().toLowerCase().startsWith("/ac ")) {
            return;
        }
        PlayerDataWindow w = plugin.window(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        if (plugin.settings().logCommands()) {
            w.recordCommand(event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        int hash = event.getMessage().hashCode();
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.window(player.getUniqueId(), player.getName()).recordChat(Integer.toString(hash)));
    }

    private double expectedOutgoingDamage(Player player, Entity target) {
        ItemStack item = player.getInventory().getItemInMainHand();
        double base = player.getAttribute(Attribute.ATTACK_DAMAGE) == null
                ? 1.0
                : player.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
        return DamageCalculator.meleeExpected(
                base,
                item.getEnchantmentLevel(Enchantment.SHARPNESS),
                item.getEnchantmentLevel(Enchantment.SMITE),
                item.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS),
                isUndead(target),
                isArthropod(target),
                player.getFallDistance() > 0 && !player.isOnGround(),
                1.0
        );
    }

    private double vehicleSpeed(Player player) {
        if (!player.isInsideVehicle()) {
            return 0;
        }
        if (player.getVehicle() instanceof LivingEntity living
                && living.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            return living.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        }
        return 0.6;
    }

    private double expectedIncomingDamage(Player player, double raw) {
        double armor = attr(player, Attribute.ARMOR);
        double toughness = attr(player, Attribute.ARMOR_TOUGHNESS);
        int protection = 0;
        int fire = 0;
        int blast = 0;
        int projectile = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null) {
                continue;
            }
            protection += piece.getEnchantmentLevel(Enchantment.PROTECTION);
            fire += piece.getEnchantmentLevel(Enchantment.FIRE_PROTECTION);
            blast += piece.getEnchantmentLevel(Enchantment.BLAST_PROTECTION);
            projectile += piece.getEnchantmentLevel(Enchantment.PROJECTILE_PROTECTION);
        }
        int resistance = player.getPotionEffect(PotionEffectType.RESISTANCE) == null
                ? 0
                : player.getPotionEffect(PotionEffectType.RESISTANCE).getAmplifier() + 1;
        return DamageCalculator.incomingExpected(
                raw,
                armor,
                toughness,
                protection,
                fire,
                blast,
                projectile,
                resistance,
                player.isHandRaised(),
                player.getAbsorptionAmount()
        );
    }

    private double attr(Player player, Attribute attribute) {
        return player.getAttribute(attribute) == null ? 0.0 : player.getAttribute(attribute).getValue();
    }

    private double safeBaseDamage(EntityDamageEvent event) {
        try {
            return event.getOriginalDamage(DamageModifier.BASE);
        } catch (Throwable ignored) {
            return event.getDamage();
        }
    }

    private void delta(PlayerDataWindow w, ItemStack item, int direction) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        int amount = item.getAmount() * direction;
        w.recordItemDelta(item.getType().name(), amount);
    }

    private boolean isSoulSand(Location location) {
        Material type = location.getWorld().getBlockAt(location.clone().subtract(0, 1, 0)).getType();
        return type.name().contains("SOUL_SAND") || type.name().contains("SOUL_SOIL");
    }

    private boolean isUndead(Entity entity) {
        return entity instanceof Zombie
                || entity instanceof Skeleton
                || entity instanceof Wither
                || entity instanceof Phantom
                || entity instanceof Drowned
                || entity instanceof Husk
                || entity instanceof Stray
                || entity instanceof WitherSkeleton
                || entity instanceof PigZombie
                || entity instanceof Zoglin
                || entity instanceof ZombieVillager;
    }

    private boolean isArthropod(Entity entity) {
        return entity instanceof Spider
                || entity instanceof CaveSpider
                || entity instanceof Silverfish
                || entity instanceof Endermite
                || entity instanceof Bee;
    }

    private record LastMove(Location location, long time) {
    }
}
