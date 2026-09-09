package com.agnes.anticheat.audit;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IllegalItemChecker {

    private static final Map<String, Integer> MAX_LEVELS = new LinkedHashMap<>();

    static {
        MAX_LEVELS.put("protection", 4);
        MAX_LEVELS.put("fire_protection", 4);
        MAX_LEVELS.put("feather_falling", 4);
        MAX_LEVELS.put("blast_protection", 4);
        MAX_LEVELS.put("projectile_protection", 4);
        MAX_LEVELS.put("thorns", 3);
        MAX_LEVELS.put("respiration", 3);
        MAX_LEVELS.put("depth_strider", 3);
        MAX_LEVELS.put("aqua_affinity", 1);
        MAX_LEVELS.put("frost_walker", 2);
        MAX_LEVELS.put("binding_curse", 1);
        MAX_LEVELS.put("soul_speed", 3);
        MAX_LEVELS.put("swift_sneak", 3);
        MAX_LEVELS.put("sharpness", 5);
        MAX_LEVELS.put("smite", 5);
        MAX_LEVELS.put("bane_of_arthropods", 5);
        MAX_LEVELS.put("knockback", 2);
        MAX_LEVELS.put("fire_aspect", 2);
        MAX_LEVELS.put("looting", 3);
        MAX_LEVELS.put("sweeping_edge", 3);
        MAX_LEVELS.put("efficiency", 5);
        MAX_LEVELS.put("silk_touch", 1);
        MAX_LEVELS.put("unbreaking", 3);
        MAX_LEVELS.put("fortune", 3);
        MAX_LEVELS.put("power", 5);
        MAX_LEVELS.put("punch", 2);
        MAX_LEVELS.put("flame", 1);
        MAX_LEVELS.put("infinity", 1);
        MAX_LEVELS.put("luck_of_the_sea", 3);
        MAX_LEVELS.put("lure", 3);
        MAX_LEVELS.put("loyalty", 3);
        MAX_LEVELS.put("impaling", 5);
        MAX_LEVELS.put("riptide", 3);
        MAX_LEVELS.put("channeling", 1);
        MAX_LEVELS.put("multishot", 1);
        MAX_LEVELS.put("piercing", 4);
        MAX_LEVELS.put("quick_charge", 3);
        MAX_LEVELS.put("mending", 1);
        MAX_LEVELS.put("vanishing_curse", 1);
        MAX_LEVELS.put("density", 5);
        MAX_LEVELS.put("breach", 4);
        MAX_LEVELS.put("wind_burst", 3);
    }

    private IllegalItemChecker() {
    }

    public static boolean isAboveMax(String enchantKey, int level) {
        Integer max = MAX_LEVELS.get(enchantKey);
        return max != null && level > max;
    }

    public static List<String> findIllegal(ItemStack item) {
        List<String> issues = new ArrayList<>();
        if (item == null || item.getType() == Material.AIR) {
            return issues;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Map<Enchantment, Integer> enchants = new LinkedHashMap<>(meta.getEnchants());
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                enchants.putAll(storageMeta.getStoredEnchants());
            }
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                String key = entry.getKey().getKey().getKey();
                int level = entry.getValue();
                if (isAboveMax(key, level)) {
                    issues.add("附魔 " + key + "=" + level + "（正常上限 " + MAX_LEVELS.get(key) + "）");
                }
            }
        }

        int maxStack = item.getType().getMaxStackSize();
        if (item.getAmount() > maxStack) {
            issues.add("堆叠数量 " + item.getAmount() + "（正常上限 " + maxStack + "）");
        }
        return issues;
    }
}
