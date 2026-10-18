package com.haven.kgent.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class PaceProfileTest {
    @Test
    public void firstKilometerIsFasterAndLaterSplitsAreStable() {
        for (long seed : new long[] {0, 17, 1780000000000L}) {
            PaceProfile p = new PaceProfile(5000, true, seed);
            double first = p.effortAt(1000);
            for (int km = 2; km <= 5; km++) {
                double split = p.effortAt(km * 1000) - p.effortAt((km - 1) * 1000);
                assertTrue(first < split * 0.97);
                assertEquals(1015, split, 12);
            }
            assertEquals(5000, p.effortAt(5000), 0);
            assertEquals(5000, p.distanceAt(5000), 0);
        }
    }

    @Test
    public void integrationIsInvertibleAndSpeedChangesSmoothly() {
        PaceProfile p = new PaceProfile(5454.54545, true, 25);
        double priorDistance = 0, priorSpeed = p.speedFactorAt(0);
        for (double effort = 0; effort < 5454; effort += 2.5) {
            double distance = p.distanceAt(effort);
            assertTrue(distance >= priorDistance);
            assertEquals(effort, p.effortAt(distance), 1e-7);
            double speed = p.speedFactorAt(distance);
            assertTrue(Math.abs(speed - priorSpeed) < 0.002);
            assertTrue(speed > 0.94 && speed < 1.11);
            priorDistance = distance;
            priorSpeed = speed;
        }
    }

    @Test
    public void sameRecordHasSameCurveAndDifferentRecordsVary() {
        PaceProfile a = new PaceProfile(5000, true, 42);
        PaceProfile b = new PaceProfile(5000, true, 42);
        PaceProfile c = new PaceProfile(5000, true, 43);
        assertEquals(a.distanceAt(1234), b.distanceAt(1234), 0);
        assertNotEquals(a.distanceAt(1234), c.distanceAt(1234), 1e-4);
    }

    @Test
    public void uniformModeAndShortRoutesPreserveTotals() {
        PaceProfile uniform = new PaceProfile(800, false, 42);
        assertEquals(123.5, uniform.distanceAt(123.5), 1e-9);
        assertEquals(1, uniform.speedFactorAt(123.5), 0);
        PaceProfile shortRun = new PaceProfile(120, true, 42);
        assertEquals(120, shortRun.distanceAt(1000), 0);
        assertEquals(0, shortRun.distanceAt(-1), 0);
        assertTrue(shortRun.maxSpeedFactor() < 1.02);
    }
}
