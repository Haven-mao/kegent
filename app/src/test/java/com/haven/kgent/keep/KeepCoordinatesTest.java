package com.haven.kgent.keep;

import static org.junit.Assert.*;

import com.haven.kgent.core.RunRoute;

import org.junit.Test;

public class KeepCoordinatesTest {
    @Test
    public void gpsControlPointUsesTheDomesticMapDatum() {
        RunRoute.Point p = KeepCoordinates.fromGps(new RunRoute.Point(39.916527, 116.397128));
        assertEquals(39.9179307492, p.latitude, 0.000001);
        assertEquals(116.4033724940, p.longitude, 0.000001);
    }

    @Test
    public void outsideCoverageKeepsGpsCoordinates() {
        RunRoute.Point london = new RunRoute.Point(51.5074, -0.1278);
        assertSame(london, KeepCoordinates.fromGps(london));
    }
}
