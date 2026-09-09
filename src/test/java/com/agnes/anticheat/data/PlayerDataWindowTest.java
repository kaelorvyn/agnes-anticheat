package com.agnes.anticheat.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataWindowTest {

    @Test
    void p95IgnoresSingleSpeedSpike() {
        PlayerDataWindow window = new PlayerDataWindow(UUID.randomUUID(), "test");
        window.recordMove(10.0, 0.0, true, false, false, false, false, 0, false, false);
        for (int i = 0; i < 19; i++) {
            window.recordMove(2.0, 0.0, true, false, false, false, false, 0, false, false);
        }
        assertEquals(10.0, window.maxHorizontalSpeed, 0.001);
        assertTrue(window.p95HorizontalSpeed() <= 2.0, "95% speed should ignore one spike");
    }
}
