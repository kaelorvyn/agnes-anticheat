package com.agnes.anticheat;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TrustedAdminRegistry {

    private final AgnesAntiCheatPlugin plugin;
    private final File uuidFile;
    private YamlConfiguration uuids;
    private Set<String> names = new LinkedHashSet<>();

    public TrustedAdminRegistry(AgnesAntiCheatPlugin plugin) {
        this.plugin = plugin;
        this.uuidFile = new File(plugin.getDataFolder(), "trusted-uuids.yml");
        load();
    }

    public void load() {
        names = new LinkedHashSet<>();
        for (String raw : plugin.getConfig().getStringList("trusted-admins")) {
            names.add(raw.toLowerCase());
        }
        uuids = YamlConfiguration.loadConfiguration(uuidFile);
    }

    public boolean isTrusted(UUID uuid, String name) {
        String key = name.toLowerCase();
        if (!names.contains(key)) {
            return false;
        }
        String bound = uuids.getString("uuids." + key);
        return bound != null && bound.equalsIgnoreCase(uuid.toString());
    }

    public boolean shouldBind(UUID uuid, String name) {
        String key = name.toLowerCase();
        if (!names.contains(key)) {
            return false;
        }
        String bound = uuids.getString("uuids." + key);
        return bound == null || bound.equalsIgnoreCase(uuid.toString());
    }

    public void bind(UUID uuid, String name) {
        String key = name.toLowerCase();
        if (!shouldBind(uuid, name)) {
            plugin.getLogger().warning("拒绝将信任名字 " + name + " 绑定到不同的 UUID。");
            return;
        }
        uuids.set("uuids." + key, uuid.toString());
        saveUuids();
    }

    public boolean add(String name) {
        boolean added = names.add(name.toLowerCase());
        if (added) {
            saveConfigNames();
        }
        return added;
    }

    public boolean remove(String name) {
        String key = name.toLowerCase();
        boolean removed = names.remove(key);
        uuids.set("uuids." + key, null);
        saveUuids();
        if (removed) {
            saveConfigNames();
        }
        return removed;
    }

    public Set<String> names() {
        return Set.copyOf(names);
    }

    private void saveConfigNames() {
        plugin.getConfig().set("trusted-admins", List.copyOf(names));
        plugin.saveConfig();
    }

    private void saveUuids() {
        try {
            uuids.save(uuidFile);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存信任 UUID 文件：" + e.getMessage());
        }
    }
}
