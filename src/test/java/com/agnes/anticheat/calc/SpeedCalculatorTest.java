package com.agnes.anticheat.calc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedCalculatorTest {

    @Test
    void sprintRaisesLegalGroundSpeed() {
        double walk = SpeedCalculator.groundMax(4.317, false, false, 0.0);
        double sprint = SpeedCalculator.groundMax(4.317, true, false, 0.0);
        assertTrue(sprint > walk);
        assertEquals(5.612, sprint, 0.001);
    }

    @Test
    void toleranceRaisesMaximum() {
        double value = SpeedCalculator.groundMax(4.317, true, true, 0.25);
        assertEquals(8.90875, value, 0.001);
    }

    @Test
    void sprintJumpIsIncluded() {
        double value = SpeedCalculator.groundMax(4.317, true, true, 0.0);
        assertEquals(7.127, value, 0.001);
    }

    @Test
    void highPingRaisesTolerance() {
        assertTrue(SpeedCalculator.pingTolerance(0.25, 500) > 0.25);
        assertEquals(0.65, SpeedCalculator.pingTolerance(0.25, 500), 0.001);
    }

    @Test
    void speedAndSlownessEffectsScaleMovement() {
        assertEquals(1.0, SpeedCalculator.effectMultiplier(0, 0), 0.001);
        assertEquals(1.4, SpeedCalculator.effectMultiplier(2, 0), 0.001);
        assertEquals(0.85, SpeedCalculator.effectMultiplier(0, 1), 0.001);
    }
}
