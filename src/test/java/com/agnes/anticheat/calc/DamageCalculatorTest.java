package com.agnes.anticheat.calc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageCalculatorTest {

    @Test
    void sharpnessAndCriticalAreApplied() {
        double normal = DamageCalculator.meleeExpected(7, 5, 0, 0, false, false, false, 1.0);
        assertEquals(10.0, normal, 0.001);
        double critical = DamageCalculator.meleeExpected(7, 5, 0, 0, false, false, true, 1.0);
        assertEquals(15.0, critical, 0.001);
        double cooldown = DamageCalculator.meleeExpected(7, 5, 0, 0, false, false, false, 0.5);
        assertEquals(5.0, cooldown, 0.001);
    }

    @Test
    void incomingDamageIsReducedByArmor() {
        double reduced = DamageCalculator.incomingExpected(
                10, 20, 8, 0, 0, 0, 0, 0, false, 0
        );
        assertTrue(reduced < 10);
    }

    @Test
    void deviationComparesActualToExpected() {
        assertEquals(0.0, DamageCalculator.deviation(10, 10), 0.001);
        assertTrue(DamageCalculator.deviation(10, 12) > 0.15);
    }
}
