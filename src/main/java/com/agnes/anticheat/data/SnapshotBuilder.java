package com.agnes.anticheat.data;

import com.agnes.anticheat.Settings;
import com.agnes.anticheat.calc.DamageCalculator;
import com.agnes.anticheat.calc.SpeedCalculator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SnapshotBuilder {

    private SnapshotBuilder() {
    }

    public static JsonObject build(Player player, PlayerDataWindow window, Settings settings) {
        JsonObject out = new JsonObject();
        out.addProperty("player", player.getName());
        out.addProperty("uuid", player.getUniqueId().toString());
        out.addProperty("world", player.getWorld().getName());
        out.addProperty("gamemode", player.getGameMode().name());
        out.addProperty("ping", player.getPing());
        out.addProperty("tps", safeTps());
        out.addProperty("client_brand", safe(() -> player.getClientBrandName(), ""));
        out.add("movement", movement(player, window, settings));
        out.add("combat", combat(player, window, settings));
        out.add("inventory", inventory(window));
        out.add("interaction", interaction(window, settings));
        out.add("status", status(player, window));
        out.add("packets", packets(window));
        out.add("local_flags", flags(player, window, settings));
        return out;
    }

    private static JsonObject movement(Player player, PlayerDataWindow w, Settings s) {
        JsonObject o = new JsonObject();
        double tolerance = SpeedCalculator.pingTolerance(s.speedTolerance(), player.getPing());
        o.addProperty("samples", w.movementSamples);
        o.addProperty("max_horizontal_speed", round(w.maxHorizontalSpeed));
        o.addProperty("p95_horizontal_speed", round(w.p95HorizontalSpeed()));
        o.addProperty("max_vertical_speed", round(w.maxVerticalSpeed));
        o.addProperty("air_ratio", w.movementSamples == 0 ? 0 : round((double) w.airSamples / w.movementSamples));
        o.addProperty("jumps", w.jumpCount);
        o.addProperty("teleports", w.teleportCount);
        o.addProperty("flight", w.sawFlight || player.isFlying());
        o.addProperty("elytra", w.sawElytra || player.isGliding());
        o.addProperty("vehicle", w.sawVehicle || player.isInsideVehicle());
        o.addProperty("swimming", w.sawSwim || player.isSwimming());
        o.addProperty("soul_sand", w.sawSoulSand || isSoulSand(player.getLocation()));
        o.addProperty("allow_flight", player.getAllowFlight());
        o.addProperty("base_movement_speed", round(baseSpeed(player)));
        o.addProperty("speed_effect_level", effectLevel(player, PotionEffectType.SPEED));
        o.addProperty("slowness_effect_level", effectLevel(player, PotionEffectType.SLOWNESS));
        o.addProperty("speed_effect_multiplier", round(SpeedCalculator.effectMultiplier(
                effectLevel(player, PotionEffectType.SPEED),
                effectLevel(player, PotionEffectType.SLOWNESS)
        )));
        o.addProperty("expected_max_speed", round(expectedMaxSpeed(player, w, tolerance)));
        o.addProperty("effective_speed_tolerance", round(tolerance));
        o.addProperty("speed_deviation", round(SpeedCalculator.deviation(expectedMaxSpeed(player, w, tolerance), w.maxHorizontalSpeed)));
        o.addProperty("ping_tier", player.getPing() > 300 ? "high" : "normal");
        return o;
    }

    private static JsonObject combat(Player player, PlayerDataWindow w, Settings s) {
        JsonObject o = new JsonObject();
        o.addProperty("attacks", w.attacks);
        o.addProperty("swing_packets", w.swingPackets);
        o.addProperty("interact_packets", w.interactPackets);
        o.addProperty("attack_packets", w.attackPackets);
        o.addProperty("max_attack_distance", round(w.maxAttackDistance));
        o.addProperty("avg_attack_distance", w.attacks == 0 ? 0 : round(w.sumAttackDistance / w.attacks));
        o.addProperty("distinct_targets", w.distinctTargets);
        o.addProperty("max_cps", round(w.maxCps));

        ItemStack item = player.getInventory().getItemInMainHand();
        double baseDamage = attr(player, Attribute.ATTACK_DAMAGE, 1.0);
        double expectedMelee = DamageCalculator.meleeExpected(
                baseDamage,
                level(item, Enchantment.SHARPNESS),
                level(item, Enchantment.SMITE),
                level(item, Enchantment.BANE_OF_ARTHROPODS),
                false,
                false,
                false,
                1.0
        );
        o.addProperty("expected_melee_damage", round(expectedMelee));
        o.addProperty("actual_outgoing_raw", round(w.outgoingRawTotal));
        o.addProperty("expected_outgoing_raw", round(w.outgoingExpectedTotal));
        o.addProperty("outgoing_deviation", round(w.maxOutgoingDeviation));
        o.addProperty("incoming_hits", w.incomingHits);
        o.addProperty("incoming_raw", round(w.incomingRawTotal));
        o.addProperty("incoming_expected", round(w.incomingExpectedTotal));
        o.addProperty("incoming_actual", round(w.incomingActualTotal));
        o.addProperty("incoming_deviation", round(w.maxIncomingDeviation));
        return o;
    }

    private static JsonObject inventory(PlayerDataWindow w) {
        JsonObject o = new JsonObject();
        o.addProperty("clicks", w.clickCount);
        o.addProperty("drags", w.dragCount);
        o.addProperty("creative_actions", w.creativeActions);
        o.addProperty("container_opens", w.containerOpens);
        o.addProperty("pickups", w.itemPickups);
        o.addProperty("drops", w.itemDrops);
        o.addProperty("crafts", w.craftCount);
        o.addProperty("furnace_extracts", w.furnaceExtractCount);
        o.addProperty("suspicious_item_events", w.suspiciousItemEvents);
        JsonObject deltas = new JsonObject();
        for (Map.Entry<String, Integer> e : w.itemDeltas.entrySet()) {
            deltas.addProperty(e.getKey(), e.getValue());
        }
        o.add("item_deltas", deltas);
        return o;
    }

    private static JsonObject interaction(PlayerDataWindow w, Settings s) {
        JsonObject o = new JsonObject();
        o.addProperty("block_breaks", w.blockBreaks);
        o.addProperty("block_places", w.blockPlaces);
        o.addProperty("max_block_distance", round(w.maxBlockDistance));
        o.addProperty("chat_messages", w.chatMessages);
        o.addProperty("duplicate_chat_messages", w.duplicateChatMessages);
        JsonArray commands = new JsonArray();
        if (s.logCommands()) {
            for (String command : w.commands) {
                commands.add(command);
            }
        }
        o.add("commands", commands);
        JsonArray modes = new JsonArray();
        for (String mode : w.gamemodeChanges) {
            modes.add(mode);
        }
        o.add("gamemode_changes", modes);
        return o;
    }

    private static JsonObject status(Player player, PlayerDataWindow w) {
        JsonObject o = new JsonObject();
        o.addProperty("fall_distance", player.getFallDistance());
        o.addProperty("fire_ticks", player.getFireTicks());
        o.addProperty("no_damage_ticks", player.getNoDamageTicks());
        o.addProperty("absorption_hearts", player.getAbsorptionAmount());
        JsonArray effects = new JsonArray();
        player.getActivePotionEffects().forEach(effect ->
                effects.add(effect.getType().getKey().getKey() + ":" + effect.getAmplifier()));
        o.add("effects", effects);
        o.add("window_effects", toArray(w.effects));
        return o;
    }

    private static JsonObject packets(PlayerDataWindow w) {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Integer> e : w.packetCounts.entrySet()) {
            o.addProperty(e.getKey() + "_per_second", round(e.getValue() / 60.0));
        }
        return o;
    }

    private static JsonArray flags(Player player, PlayerDataWindow w, Settings s) {
        List<String> flags = new ArrayList<>();
        double tolerance = SpeedCalculator.pingTolerance(s.speedTolerance(), player.getPing());
        double expectedSpeed = expectedMaxSpeed(player, w, tolerance);
        if (w.p95HorizontalSpeed() > expectedSpeed && w.p95HorizontalSpeed() > 0.2) {
            flags.add("speed-exceeded");
        }
        if (w.maxOutgoingDeviation > s.damageTolerance()) {
            flags.add("damage-exceeded");
        }
        if (w.maxIncomingDeviation < -s.damageTolerance()) {
            flags.add("invulnerability-suspected");
        }
        if (w.suspiciousItemEvents > 0) {
            flags.add("item-anomaly");
        }
        for (int delta : w.itemDeltas.values()) {
            if (Math.abs(delta) > 128) {
                flags.add("duplication-suspected");
                break;
            }
        }
        if (w.creativeActions > 3) {
            flags.add("creative-inventory-abuse");
        }
        if (w.gamemodeChanges.stream().anyMatch(m -> m.contains("CREATIVE"))) {
            flags.add("gamemode-change");
        }
        if (w.commands.stream().anyMatch(c -> c.startsWith("/op ") || c.startsWith("/deop "))) {
            flags.add("command-abuse");
        }
        if (w.chatMessages > 15 && w.duplicateChatMessages > 8) {
            flags.add("spam");
        }
        if (w.blockBreaks > 120) {
            flags.add("fastbreak-suspected");
        }
        JsonArray arr = new JsonArray();
        flags.forEach(arr::add);
        return arr;
    }

    private static double expectedMaxSpeed(Player player, PlayerDataWindow w, double tolerance) {
        double base = baseSpeed(player);
        double max = SpeedCalculator.groundMax(base, player.isSprinting(), w.jumpCount > 0, tolerance);
        if (w.sawSwim || player.isSwimming()) {
            max = Math.max(max, SpeedCalculator.swimMax(base, level(player.getInventory().getBoots(), Enchantment.DEPTH_STRIDER), tolerance));
        }
        int soulSpeed = level(player.getInventory().getBoots(), Enchantment.SOUL_SPEED);
        if (soulSpeed > 0 && (w.sawSoulSand || isSoulSand(player.getLocation()))) {
            max = Math.max(max, SpeedCalculator.soulSandMax(base, soulSpeed, tolerance));
        }
        if (w.sawElytra || player.isGliding()) {
            max = Math.max(max, SpeedCalculator.elytraMax(tolerance));
        }
        if (w.sawFlight || player.isFlying()) {
            max = Math.max(max, SpeedCalculator.flightMax(player.getFlySpeed(), tolerance));
        }
        if (w.sawVehicle || player.isInsideVehicle()) {
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof LivingEntity living && living.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                max = Math.max(max, SpeedCalculator.vehicleMax(living.getAttribute(Attribute.MOVEMENT_SPEED).getValue(), tolerance));
            } else {
                double vehicleSpeed = w.maxVehicleSpeed > 0 ? w.maxVehicleSpeed : 0.6;
                max = Math.max(max, SpeedCalculator.vehicleMax(vehicleSpeed, tolerance));
            }
        }
        return max;
    }

    private static double baseSpeed(Player player) {
        if (player.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            return player.getAttribute(Attribute.MOVEMENT_SPEED).getValue() / 0.1 * 4.317;
        }
        return player.getWalkSpeed() / 0.2 * 4.317;
    }

    private static int effectLevel(Player player, PotionEffectType type) {
        return player.getPotionEffect(type) == null
                ? 0
                : player.getPotionEffect(type).getAmplifier() + 1;
    }

    private static double attr(Player player, Attribute attribute, double fallback) {
        return player.getAttribute(attribute) == null ? fallback : player.getAttribute(attribute).getValue();
    }

    private static boolean isSoulSand(Location location) {
        Material type = location.getWorld().getBlockAt(location.clone().subtract(0, 1, 0)).getType();
        return type.name().contains("SOUL_SAND") || type.name().contains("SOUL_SOIL");
    }

    private static int level(ItemStack item, Enchantment enchantment) {
        return item == null || enchantment == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    private static JsonArray toArray(java.util.Set<String> values) {
        JsonArray arr = new JsonArray();
        values.forEach(arr::add);
        return arr;
    }

    private static double safeTps() {
        try {
            return Bukkit.getTPS()[0];
        } catch (Throwable ignored) {
            return 20.0;
        }
    }

    private static String safe(java.util.function.Supplier<String> supplier, String fallback) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
