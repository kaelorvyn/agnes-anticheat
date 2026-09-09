package com.agnes.anticheat.calc;

public final class DamageCalculator {

    private DamageCalculator() {
    }

    public static double meleeExpected(
            double baseAttackDamage,
            int sharpness,
            int smite,
            int baneOfArthropods,
            boolean targetUndead,
            boolean targetArthropod,
            boolean critical,
            double cooldownFactor
    ) {
        double damage = Math.max(0, baseAttackDamage);
        damage += 0.5 * sharpness + 0.5;
        if (targetUndead) {
            damage += 2.5 * smite;
        }
        if (targetArthropod) {
            damage += 2.5 * baneOfArthropods;
        }
        if (critical) {
            damage *= 1.5;
        }
        damage *= Math.max(0.2, Math.min(1.0, cooldownFactor));
        return damage;
    }

    public static double rangedExpected(double baseArrowDamage, int powerLevel, boolean critical) {
        double damage = Math.max(0, baseArrowDamage) + 0.25 * (powerLevel + 1);
        if (critical) {
            damage += Math.random() * (damage * 0.5);
        }
        return damage;
    }

    public static double incomingExpected(
            double baseRawDamage,
            double armor,
            double toughness,
            int protection,
            int fireProtection,
            int blastProtection,
            int projectileProtection,
            int resistanceLevel,
            boolean blocking,
            double absorptionHearts
    ) {
        double damage = Math.max(0, baseRawDamage);
        double defense = Math.min(20, Math.max(armor / 5.0, armor - damage / (2.0 + toughness / 4.0)));
        damage *= Math.max(0, 1.0 - defense / 25.0);

        int epf = protection + 2 * fireProtection + 2 * blastProtection + 2 * projectileProtection;
        epf = Math.min(20, epf);
        damage *= Math.max(0, 1.0 - epf * 0.04);

        damage *= Math.max(0, 1.0 - 0.2 * Math.max(0, resistanceLevel));
        if (blocking) {
            damage *= 0.5;
        }
        damage = Math.max(0, damage - Math.max(0, absorptionHearts));
        return damage;
    }

    public static double deviation(double expected, double actual) {
        if (expected <= 0) {
            return actual > 0 ? 1.0 : 0.0;
        }
        return (actual - expected) / expected;
    }
}
