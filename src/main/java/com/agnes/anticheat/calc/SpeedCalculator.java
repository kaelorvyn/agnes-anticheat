package com.agnes.anticheat.calc;

public final class SpeedCalculator {

    private static final double WALK_BPS = 4.317;
    private static final double SPRINT_BPS = 5.612;
    private static final double SPRINT_JUMP_BPS = 7.127;
    private static final double SPRINT_SWIM_BPS = 3.918;
    private static final double DEPTH_STRIDER_III_BPS = 5.305;

    private SpeedCalculator() {
    }

    public static double groundMax(double walkBps, boolean sprinting, boolean jumping, double tolerance) {
        double speed = walkBps;
        if (sprinting) {
            speed = walkBps / WALK_BPS * SPRINT_BPS;
        }
        if (jumping) {
            speed = Math.max(speed, walkBps / WALK_BPS * SPRINT_JUMP_BPS);
        }
        return speed * (1.0 + tolerance);
    }

    public static double swimMax(double walkBps, int depthStrider, double tolerance) {
        double speed = walkBps / WALK_BPS * SPRINT_SWIM_BPS;
        int level = Math.max(0, Math.min(3, depthStrider));
        speed += (DEPTH_STRIDER_III_BPS - speed) * level / 3.0;
        return speed * (1.0 + tolerance);
    }

    public static double soulSandMax(double walkBps, int soulSpeed, double tolerance) {
        double multiplier = (Math.max(0, soulSpeed) * 0.105) + 1.3;
        double speed = walkBps * multiplier;
        return speed * (1.0 + tolerance);
    }

    public static double elytraMax(double tolerance) {
        return 50.0 * (1.0 + tolerance);
    }

    public static double flightMax(double flySpeed, double tolerance) {
        return Math.max(0.2, flySpeed * 10.0) * 20.0 * (1.0 + tolerance);
    }

    public static double vehicleMax(double vehicleSpeed, double tolerance) {
        return Math.max(0.2, vehicleSpeed * 1.6) * 20.0 * (1.0 + tolerance);
    }

    public static double effectMultiplier(int speedLevel, int slownessLevel) {
        return (1.0 + 0.2 * Math.max(0, speedLevel))
                * (1.0 - 0.15 * Math.max(0, slownessLevel));
    }

    public static double pingTolerance(double baseTolerance, int ping) {
        double extra = Math.min(2.0, Math.max(0, ping) / 1000.0 * 0.8);
        return baseTolerance + extra;
    }

    public static double deviation(double expected, double actual) {
        if (expected <= 0) {
            return actual > 0 ? 1.0 : 0.0;
        }
        return (actual - expected) / expected;
    }
}
