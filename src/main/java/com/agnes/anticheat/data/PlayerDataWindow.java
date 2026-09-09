package com.agnes.anticheat.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerDataWindow {

    public final UUID uuid;
    public final String name;

    public long windowStart;

    public int movementSamples;
    public double maxHorizontalSpeed;
    private final List<Double> horizontalSpeedSamples = new ArrayList<>();
    public double maxVerticalSpeed;
    public double maxJumpDistance;
    public int airSamples;
    public int groundSamples;
    public int jumpCount;
    public int teleportCount;
    public boolean sawFlight;
    public boolean sawElytra;
    public boolean sawVehicle;
    public boolean sawSwim;
    public boolean sawSoulSand;
    public double maxVehicleSpeed;

    public int attacks;
    public int swingPackets;
    public int interactPackets;
    public int attackPackets;
    public double maxAttackDistance;
    public double sumAttackDistance;
    public int distinctTargets;
    public final Set<UUID> targetIds = new HashSet<>();
    public double maxCps;
    public long lastAttackTime;
    public int attacksInSecond;
    public long secondStart;

    public int incomingHits;
    public double incomingRawTotal;
    public double incomingExpectedTotal;
    public double incomingActualTotal;
    public double maxIncomingDeviation;
    public double outgoingRawTotal;
    public double outgoingExpectedTotal;
    public double maxOutgoingDeviation;

    public int clickCount;
    public int dragCount;
    public int creativeActions;
    public int containerOpens;
    public int itemPickups;
    public int itemDrops;
    public int craftCount;
    public int furnaceExtractCount;
    public int suspiciousItemEvents;
    public final Map<String, Integer> itemDeltas = new HashMap<>();

    public int blockBreaks;
    public int blockPlaces;
    public double maxBlockDistance;

    public int chatMessages;
    public int duplicateChatMessages;
    private final Set<Integer> chatHashes = new HashSet<>();

    public final List<String> commands = new ArrayList<>();
    public final List<String> gamemodeChanges = new ArrayList<>();
    public final Set<String> effects = new HashSet<>();
    public int maxFireTicks;
    public int maxFallDistance;
    public int maxNoDamageTicks;

    public final Map<String, Integer> packetCounts = new HashMap<>();

    public int ping;
    public double tps;
    public int serverTick;

    public PlayerDataWindow(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.windowStart = System.currentTimeMillis();
        this.secondStart = System.currentTimeMillis();
    }

    public void recordMove(
            double horizontalSpeed,
            double verticalSpeed,
            boolean onGround,
            boolean jumping,
            boolean flight,
            boolean elytra,
            boolean vehicle,
            double vehicleSpeed,
            boolean swimming,
            boolean soulSand
    ) {
        movementSamples++;
        maxHorizontalSpeed = Math.max(maxHorizontalSpeed, horizontalSpeed);
        if (horizontalSpeed > 0.2) {
            horizontalSpeedSamples.add(horizontalSpeed);
            if (horizontalSpeedSamples.size() > 2400) {
                horizontalSpeedSamples.remove(0);
            }
        }
        maxVerticalSpeed = Math.max(maxVerticalSpeed, Math.abs(verticalSpeed));
        if (onGround) {
            groundSamples++;
        } else {
            airSamples++;
        }
        if (jumping) {
            jumpCount++;
        }
        sawFlight |= flight;
        sawElytra |= elytra;
        sawVehicle |= vehicle;
        if (vehicle && vehicleSpeed > 0) {
            maxVehicleSpeed = Math.max(maxVehicleSpeed, vehicleSpeed);
        }
        sawSwim |= swimming;
        sawSoulSand |= soulSand;
    }

    public void recordTeleport() {
        teleportCount++;
    }

    public void recordAttack(double distance, int damage) {
        attacks++;
        attackPackets++;
        maxAttackDistance = Math.max(maxAttackDistance, distance);
        sumAttackDistance += distance;

        long now = System.currentTimeMillis();
        if (now - secondStart >= 1000) {
            maxCps = Math.max(maxCps, attacksInSecond);
            attacksInSecond = 0;
            secondStart = now;
        }
        attacksInSecond++;
        maxCps = Math.max(maxCps, attacksInSecond);
        lastAttackTime = now;
    }

    public void recordOutgoingDamage(double actualRaw, double expectedRaw) {
        outgoingRawTotal += actualRaw;
        outgoingExpectedTotal += expectedRaw;
        double deviation = expectedRaw <= 0
                ? (actualRaw > 0 ? 1.0 : 0.0)
                : (actualRaw - expectedRaw) / expectedRaw;
        maxOutgoingDeviation = Math.max(maxOutgoingDeviation, deviation);
    }

    public void recordIncomingDamage(double raw, double expectedFinal, double actualFinal) {
        incomingHits++;
        incomingRawTotal += raw;
        incomingExpectedTotal += expectedFinal;
        incomingActualTotal += actualFinal;
        double deviation = expectedFinal <= 0
                ? (actualFinal > 0 ? 1.0 : 0.0)
                : (actualFinal - expectedFinal) / expectedFinal;
        maxIncomingDeviation = Math.max(maxIncomingDeviation, deviation);
    }

    public void recordItemDelta(String key, int delta) {
        itemDeltas.merge(key, delta, Integer::sum);
        if (Math.abs(delta) > 64 || itemDeltas.get(key) > 128) {
            suspiciousItemEvents++;
        }
    }

    public void recordCommand(String command) {
        if (commands.size() < 20) {
            commands.add(command.length() > 120 ? command.substring(0, 120) : command);
        }
    }

    public void recordChat(String message) {
        chatMessages++;
        int hash = message.hashCode();
        if (!chatHashes.add(hash)) {
            duplicateChatMessages++;
        }
    }

    public void recordPacket(String packetName) {
        packetCounts.merge(packetName, 1, Integer::sum);
    }

    public void reset() {
        windowStart = System.currentTimeMillis();
        movementSamples = 0;
        maxHorizontalSpeed = 0;
        horizontalSpeedSamples.clear();
        maxVerticalSpeed = 0;
        maxJumpDistance = 0;
        airSamples = 0;
        groundSamples = 0;
        jumpCount = 0;
        teleportCount = 0;
        sawFlight = false;
        sawElytra = false;
        sawVehicle = false;
        maxVehicleSpeed = 0;
        sawSwim = false;
        sawSoulSand = false;

        attacks = 0;
        swingPackets = 0;
        interactPackets = 0;
        attackPackets = 0;
        maxAttackDistance = 0;
        sumAttackDistance = 0;
        distinctTargets = targetIds.size();
        targetIds.clear();
        maxCps = 0;
        attacksInSecond = 0;
        secondStart = System.currentTimeMillis();

        incomingHits = 0;
        incomingRawTotal = 0;
        incomingExpectedTotal = 0;
        incomingActualTotal = 0;
        maxIncomingDeviation = 0;
        outgoingRawTotal = 0;
        outgoingExpectedTotal = 0;
        maxOutgoingDeviation = 0;

        clickCount = 0;
        dragCount = 0;
        creativeActions = 0;
        containerOpens = 0;
        itemPickups = 0;
        itemDrops = 0;
        craftCount = 0;
        furnaceExtractCount = 0;
        suspiciousItemEvents = 0;
        itemDeltas.clear();

        blockBreaks = 0;
        blockPlaces = 0;
        maxBlockDistance = 0;

        chatMessages = 0;
        duplicateChatMessages = 0;
        chatHashes.clear();

        commands.clear();
        gamemodeChanges.clear();
        effects.clear();
        maxFireTicks = 0;
        maxFallDistance = 0;
        maxNoDamageTicks = 0;

        packetCounts.clear();
    }

    public double p95HorizontalSpeed() {
        if (horizontalSpeedSamples.isEmpty()) {
            return maxHorizontalSpeed;
        }
        List<Double> sorted = new ArrayList<>(horizontalSpeedSamples);
        sorted.sort(Double::compareTo);
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index);
    }
}
