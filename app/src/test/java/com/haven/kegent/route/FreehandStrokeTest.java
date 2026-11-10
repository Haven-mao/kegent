package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FreehandStrokeTest {
    private final RouteGeometry.Plane plane = new RouteGeometry.Plane(new RunRoute.Point(40, 122));
    @Test public void aDenseStraightStrokeBecomesTwoEndpoints() {
        List<RunRoute.Point> raw = new ArrayList<>();
        for (int i = 0; i <= 200; i++) raw.add(plane.point(i, .1 * Math.sin(i)));
        List<RunRoute.Point> result = FreehandStroke.prepare(raw, 1, 500, false);
        assertEquals(2, result.size()); assertSame(raw.get(0), result.get(0)); assertSame(raw.get(200), result.get(1));
    }
    @Test public void visibleBendsRemainWithinTheRequestedTolerance() {
        List<RunRoute.Point> raw = new ArrayList<>();
        for (int i = 0; i <= 300; i++) raw.add(plane.point(i / 2.0, 40 * Math.sin(i / 40.0)));
        List<RunRoute.Point> result = FreehandStroke.prepare(raw, 2, 500, false);
        assertTrue(result.size() > 3); assertTrue(result.size() < raw.size());
        for (RunRoute.Point p : raw) {
            double nearest = Double.MAX_VALUE;
            for (int i = 1; i < result.size(); i++) nearest = Math.min(nearest,
                    RouteGeometry.segmentDistance(plane.xy(p), plane.xy(result.get(i - 1)), plane.xy(result.get(i))));
            assertTrue(nearest <= 2.001);
        }
        assertEquals(301, raw.size());
    }
    @Test public void roadStrokeCanBeSimplifiedWithoutTruncatingItsDestination() {
        List<RunRoute.Point> raw = new ArrayList<>();
        for (int i = 0; i <= 150; i++) raw.add(plane.point(i, 20 * Math.sin(i / 25.0)));
        List<RunRoute.Point> result = FreehandStroke.prepare(raw, 1, 20, true);
        assertTrue(result.size() <= 20); assertSame(raw.get(150), result.get(result.size() - 1));
    }
    @Test(expected = IllegalArgumentException.class) public void aComplicatedRoadStrokeIsRejectedInsteadOfTruncated() {
        List<RunRoute.Point> raw = new ArrayList<>();
        for (int i = 0; i < 50; i++) raw.add(plane.point(i * 40, i % 2 * 80));
        FreehandStroke.prepare(raw, 1, 20, true);
    }
    @Test(expected = IllegalArgumentException.class) public void aTapDoesNotBecomeATinyRoute() {
        FreehandStroke.prepare(Arrays.asList(plane.point(0, 0), plane.point(.5, 0)), 1, 500, false);
    }
    @Test(expected = IllegalArgumentException.class) public void fullDraftsDoNotLoseTheTailOfTheNextStroke() {
        FreehandStroke.prepare(Arrays.asList(plane.point(0, 0), plane.point(30, 0), plane.point(30, 30)), 1, 1, false);
    }
    @Test public void aDrawnLoopRetainsItsExactClosure() {
        List<RunRoute.Point> raw = Arrays.asList(plane.point(0, 0), plane.point(50, 0), plane.point(50, 50), plane.point(0, 50), plane.point(0, 0));
        List<RunRoute.Point> result = FreehandStroke.prepare(raw, 1, 500, false);
        assertTrue(new RunRoute("loop", result).closed); assertEquals(5, result.size());
    }
}
