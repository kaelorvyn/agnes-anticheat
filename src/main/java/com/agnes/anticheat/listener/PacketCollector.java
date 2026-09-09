package com.agnes.anticheat.listener;

import com.agnes.anticheat.AgnesAntiCheatPlugin;
import com.agnes.anticheat.data.PlayerDataWindow;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PacketCollector extends com.github.retrooper.packetevents.event.SimplePacketListenerAbstract {

    private final AgnesAntiCheatPlugin plugin;

    public PacketCollector(AgnesAntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        Object rawPlayer = event.getPlayer();
        if (!(rawPlayer instanceof Player player)) {
            return;
        }
        PlayerDataWindow window = plugin.window(player.getUniqueId(), player.getName());
        PacketType.Play.Client type = event.getPacketType();
        window.recordPacket(type.name());

        switch (type) {
            case ATTACK -> window.attackPackets++;
            case ANIMATION -> window.swingPackets++;
            case INTERACT_ENTITY -> window.interactPackets++;
            case PLAYER_FLYING -> window.sawFlight = true;
            case VEHICLE_MOVE -> window.sawVehicle = true;
            case PLAYER_ABILITIES -> window.sawFlight = true;
            case CLICK_WINDOW -> window.clickCount++;
            case CREATIVE_INVENTORY_ACTION -> window.creativeActions++;
            case CLOSE_WINDOW -> {
                // Counted by Bukkit container open/close when possible.
            }
            case PLAYER_DIGGING -> window.blockBreaks++;
            case PLAYER_BLOCK_PLACEMENT -> window.blockPlaces++;
            case CHAT_COMMAND, CHAT_MESSAGE -> {
                // Command content is captured by Bukkit; chat content is intentionally not retained.
            }
            default -> {
            }
        }
    }

    public PacketListenerCommon register() {
        return com.github.retrooper.packetevents.PacketEvents.getAPI()
                .getEventManager()
                .registerListener(this);
    }

    public void unregister(PacketListenerCommon handle) {
        if (handle != null) {
            com.github.retrooper.packetevents.PacketEvents.getAPI()
                    .getEventManager()
                    .unregisterListener(handle);
        }
    }
}
