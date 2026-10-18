package com.haven.kgent.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class RunEngineTest {
    @Test
    public void pauseAndResumeExcludePausedTime() {
        RunEngine e = new RunEngine();
        e.start(1000, 3, 1000);
        e.pause(11000);
        e.tick(71000);
        e.resume(81000);
        e.tick(91000);
        assertEquals(60, e.snapshot().distance, 0.0001);
        assertEquals(20000, e.snapshot().elapsedMillis);
    }

    @Test
    public void speedChangeIntegratesPreviousSpeedBeforeChange() {
        RunEngine e = new RunEngine();
        e.start(1000, 2, 0);
        e.updateSpeed(4, 10000);
        e.tick(20000);
        assertEquals(60, e.snapshot().distance, 0.0001);
    }

    @Test
    public void finishClampsDistanceAndDuration() {
        RunEngine e = new RunEngine();
        e.start(10, 2, 0);
        e.tick(10000);
        assertEquals(RunEngine.State.FINISHED, e.snapshot().state);
        assertEquals(10, e.snapshot().distance, 0.0001);
        assertEquals(5000, e.snapshot().elapsedMillis);
    }

    @Test(expected = IllegalStateException.class)
    public void cannotStartDuplicateLoop() {
        RunEngine e = new RunEngine();
        e.start(1000, 3, 0);
        e.start(1000, 3, 100);
    }
}
