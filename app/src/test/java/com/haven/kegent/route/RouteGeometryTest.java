package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RouteGeometryTest {
    @Test public void existingTrackFitUsesItsCenterAndBearingRatherThanItsStartingPoint() {
        RouteDraft source = new RouteDraft(); source.bearing = 17; source.startFraction = .21;
        RunRoute original = new RunRoute("original", RouteGeometry.track(source));
        RouteDraft fitted = RouteGeometry.fitTrack(original); assertNotNull(fitted);
        assertTrue(RunRoute.distance(source.center, fitted.center) < .25);
        assertEquals(source.bearing, fitted.bearing, .3);
        assertEquals(source.radius, fitted.radius, .2); assertEquals(source.straight, fitted.straight, .5);
        assertTrue(RunRoute.distance(original.get(0), fitted.center) > 30);
    }
    @Test public void fittingDoesNotGuessATrackFromAnOpenRoute() {
        assertNull(RouteGeometry.fitTrack(new RunRoute("open", Arrays.asList(new RunRoute.Point(40, 122), new RunRoute.Point(40.002, 122)))));
    }
    @Test public void routeCenterDoesNotDependOnTheChosenStartingPoint() {
        RouteDraft source = new RouteDraft(); source.bearing = 152;
        RunRoute a = new RunRoute("a", RouteGeometry.track(source)); source.startFraction = .4;
        RunRoute b = new RunRoute("b", RouteGeometry.track(source));
        assertTrue(RunRoute.distance(RouteGeometry.center(a), RouteGeometry.center(b)) < .1);
    }
    @Test public void stadiumLengthAndClosureHoldAcrossLocationsPhasesAndDirections() {
        for (double latitude : new double[] {-60, 0, 40.6869, 75})
            for (double bearing : new double[] {0, 27, 135, 291})
                for (double phase : new double[] {0, .173, .5, .999})
                    for (boolean clockwise : new boolean[] {false, true}) {
                        RouteDraft d = new RouteDraft(); d.mode = RouteDraft.Mode.TRACK;
                        d.center = new RunRoute.Point(latitude, 122); d.bearing = bearing; d.startFraction = phase; d.clockwise = clockwise;
                        RunRoute r = new RunRoute("track", RouteGeometry.track(d));
                        assertTrue(r.closed); assertEquals(400, r.lengthMeters, .5);
                        assertEquals(0, RunRoute.distance(r.get(0), r.get(r.size() - 1)), 0);
                        assertTrue(RunRoute.distance(r.at(r.lengthMeters - .1), r.at(r.lengthMeters + .1)) < .21);
                    }
    }
    @Test public void changingDirectionKeepsStartAndReversesMovement() {
        RouteDraft d = new RouteDraft(); d.startFraction = .317;
        RunRoute forward = new RunRoute("f", RouteGeometry.track(d)); d.clockwise = true;
        RunRoute reverse = new RunRoute("r", RouteGeometry.track(d));
        assertEquals(0, RunRoute.distance(forward.get(0), reverse.get(0)), .001);
        assertTrue(RunRoute.distance(forward.at(15), reverse.at(reverse.lengthMeters - 15)) < .1);
    }
    @Test public void outerLaneAddsItsOwnCircumference() {
        RouteDraft d = new RouteDraft(); d.lane = 3;
        assertEquals(400 + 2 * Math.PI * 2 * 1.22, new RunRoute("lane3", RouteGeometry.track(d)).lengthMeters, .5);
    }
    @Test public void manualInterpolationPreservesControlsAndClosesExactly() {
        RouteGeometry.Plane plane = new RouteGeometry.Plane(new RunRoute.Point(40, 122));
        List<RunRoute.Point> controls = Arrays.asList(plane.point(0, 0), plane.point(50, 0), plane.point(60, 50), plane.point(0, 60));
        List<RunRoute.Point> smooth = RouteGeometry.manual(controls, true, true);
        RunRoute r = new RunRoute("manual", smooth); assertTrue(r.closed); assertTrue(smooth.size() > controls.size());
        for (RunRoute.Point p : controls) assertTrue(smooth.stream().anyMatch(at -> RunRoute.distance(p, at) < .01));
    }
    @Test public void openSmoothingKeepsBothEndpointsAndDuplicateInputsRemainFinite() {
        RunRoute.Point a = new RunRoute.Point(40, 122), b = new RunRoute.Point(40.001, 122.001), c = new RunRoute.Point(40.001, 122.002);
        List<RunRoute.Point> points = RouteGeometry.manual(Arrays.asList(a, a, b, b, c), false, true);
        assertEquals(0, RunRoute.distance(a, points.get(0)), .0001);
        assertEquals(0, RunRoute.distance(c, points.get(points.size() - 1)), .0001);
        assertFalse(new RunRoute("open", points).closed);
    }
    @Test(expected = IllegalArgumentException.class) public void invalidTrackSizeIsRejected() {
        RouteDraft d = new RouteDraft(); d.radius = Double.NaN; RouteGeometry.track(d);
    }
}
