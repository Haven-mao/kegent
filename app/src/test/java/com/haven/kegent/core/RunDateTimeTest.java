package com.haven.kegent.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class RunDateTimeTest {
    @Test
    public void secondsAndLegacyPreferencesRoundTrip() {
        assertEquals(
                "2026-10-16 19:02:37",
                RunDateTime.format(RunDateTime.parse("2026-10-16 19:02:37")));
        assertEquals(
                "2026-10-16 19:02:00", RunDateTime.format(RunDateTime.parse("2026-10-16 19:02")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverflowingSeconds() {
        RunDateTime.parse("2026-10-16 19:02:60");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsImpossibleCalendarDate() {
        RunDateTime.parse("2026-02-29 19:02:37");
    }
}
