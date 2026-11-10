package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RouteAssemblyTest {
    private final String endpoint = "https://test.example/route";
    private final RouteGeometry.Plane plane = new RouteGeometry.Plane(new RunRoute.Point(40, 122));
    private RouteDraft road() {
        RouteDraft d = new RouteDraft();
        d.anchors.add(new RouteDraft.Anchor(plane.point(0, 0))); d.anchors.add(new RouteDraft.Anchor(plane.point(50, 0)));
        d.anchors.add(new RouteDraft.Anchor(plane.point(100, 0))); return d;
    }
    private void cache(Map<String, List<RunRoute.Point>> cache, RouteDraft d, int leg, List<RunRoute.Point> shape) {
        cache.put(WalkingRoutePlanner.key(endpoint, d.anchors.get(leg).point, d.anchors.get((leg + 1) % d.anchors.size()).point), shape);
    }
    @Test public void unresolvedLegCannotBeSavedAsStraightRoad() {
        RouteDraft d = road(); Map<String, List<RunRoute.Point>> cache = new HashMap<>();
        cache(cache, d, 0, Arrays.asList(d.anchors.get(0).point, d.anchors.get(1).point));
        RouteAssembly a = RouteAssembly.build(d, cache, endpoint); assertNull(a.route); assertEquals(1, a.pending.size());
    }
    @Test public void movingAnAnchorInvalidatesItsOldRoadGeometry() {
        RouteDraft d = road(); Map<String, List<RunRoute.Point>> cache = new HashMap<>();
        cache(cache, d, 0, Arrays.asList(d.anchors.get(0).point, d.anchors.get(1).point));
        cache(cache, d, 1, Arrays.asList(d.anchors.get(1).point, d.anchors.get(2).point));
        assertNotNull(RouteAssembly.build(d, cache, endpoint).route);
        d.anchors.get(1).point = plane.point(50, 10);
        assertNull(RouteAssembly.build(d, cache, endpoint).route);
    }
    @Test public void roadGapsAreNotSilentlyJoined() {
        RouteDraft d = road(); Map<String, List<RunRoute.Point>> cache = new HashMap<>();
        cache(cache, d, 0, Arrays.asList(plane.point(0, 0), plane.point(50, 0)));
        cache(cache, d, 1, Arrays.asList(plane.point(50, 10), plane.point(100, 0)));
        assertNull(RouteAssembly.build(d, cache, endpoint).route);
    }
    @Test public void explicitManualLegMeetsItsNeighborAtSnappedEndpoint() {
        RouteDraft d = road(); Map<String, List<RunRoute.Point>> cache = new HashMap<>();
        cache(cache, d, 0, Arrays.asList(plane.point(0, 0), plane.point(50, 10))); d.anchors.get(2).manual = true;
        RouteAssembly a = RouteAssembly.build(d, cache, endpoint); assertNotNull(a.route);
        assertEquals(0, RunRoute.distance(a.pieces.get(0).get(1), a.pieces.get(1).get(0)), .001);
    }
    @Test public void closedRoadRequiresAPlannedReturnLeg() {
        RouteDraft d = road(); d.closed = true; Map<String, List<RunRoute.Point>> cache = new HashMap<>();
        for (int i = 0; i < 2; i++) cache(cache, d, i, Arrays.asList(d.anchors.get(i).point, d.anchors.get(i + 1).point));
        assertNull(RouteAssembly.build(d, cache, endpoint).route);
        cache(cache, d, 2, Arrays.asList(d.anchors.get(2).point, d.anchors.get(0).point));
        assertTrue(RouteAssembly.build(d, cache, endpoint).route.closed);
    }
    @Test public void largeSnappingOffsetRequiresCorrection() {
        RouteDraft d = road(); d.anchors.remove(2); Map<String, List<RunRoute.Point>> cache = new HashMap<>();
        cache(cache, d, 0, Arrays.asList(plane.point(0, 250), plane.point(50, 250)));
        assertNull(RouteAssembly.build(d, cache, endpoint).route);
    }
}
