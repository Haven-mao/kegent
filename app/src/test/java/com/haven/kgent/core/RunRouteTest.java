package com.haven.kgent.core;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class RunRouteTest {
    private RunRoute line() {
        return new RunRoute(
                "test", Arrays.asList(new RunRoute.Point(0, 0), new RunRoute.Point(0, 0.001)));
    }

    @Test
    public void openRouteReversesWithoutJump() {
        RunRoute r = line();
        double d = r.lengthMeters;
        assertEquals(r.at(d * 0.75).longitude, r.at(d * 1.25).longitude, 1e-10);
        assertEquals(0, r.at(d * 2).longitude, 1e-10);
    }

    @Test
    public void equatorialDistanceAndDuplicatePointsAreHandled() {
        RunRoute r =
                new RunRoute(
                        "test",
                        Arrays.asList(
                                new RunRoute.Point(0, 0),
                                new RunRoute.Point(0, 0),
                                new RunRoute.Point(0, 0.001)));
        assertEquals(2, r.size());
        assertEquals(111.31949, r.lengthMeters, 0.001);
    }

    @Test
    public void kmlNamespaceIsSupported() throws Exception {
        String kml =
                "<kml xmlns='http://www.opengis.net/kml/2.2'><LineString><coordinates>0,0,0"
                        + " 0.001,0,0</coordinates></LineString></kml>";
        assertEquals(
                2,
                RunRoute.readKml(
                                new ByteArrayInputStream(kml.getBytes(StandardCharsets.UTF_8)),
                                "route")
                        .size());
    }

    @Test
    public void historicalDistanceMatchesTimeAndPace() {
        long start = 1700000000000L;
        HistoricalRun run =
                new HistoricalRun(
                        line(),
                        start,
                        start + 1800000,
                        360,
                        170,
                        60,
                        "Asia/Shanghai",
                        start + 3600000);
        assertEquals(5000, run.distanceMeters, 0.001);
        assertEquals(1801, run.sampleCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void futureRunIsRejected() {
        new HistoricalRun(
                line(), 1000000000, 1003600000, 360, 170, 60, "Asia/Shanghai", 1001800000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidSecondsAreRejected() {
        HistoricalRun.parsePace("5:90");
    }
}
