package com.agnes.anticheat.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IllegalItemCheckerTest {

    @Test
    void detectsIllegalEnchantLevels() {
        assertTrue(IllegalItemChecker.isAboveMax("knockback", 225));
        assertTrue(IllegalItemChecker.isAboveMax("sharpness", 99));
        assertFalse(IllegalItemChecker.isAboveMax("sharpness", 5));
        assertFalse(IllegalItemChecker.isAboveMax("unknown_custom", 999));
    }
}
