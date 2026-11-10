package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;

public class WalkingRoutePlannerTest {
    @Test public void valhallaPolyline6RetainsCoordinateOrderAndPrecision() throws Exception {
        List<RunRoute.Point> points = WalkingRoutePlanner.decodePolyline6("}gjrlAetm|gFj{@lN");
        assertEquals(2, points.size()); assertEquals(40.687247, points.get(0).latitude, 1e-6);
        assertEquals(122.117459, points.get(0).longitude, 1e-6);
        assertEquals(40.686281, points.get(1).latitude, 1e-7);
    }
    @Test public void actualResponseShapeCanBecomeARoute() throws Exception {
        JSONObject o = new JSONObject("{\"trip\":{\"status\":0,\"legs\":[{\"shape\":\"}gjrlAetm|gFj{@lN\"}]}}");
        RunRoute route = new RunRoute("road", WalkingRoutePlanner.parse(o)); assertTrue(route.lengthMeters > 100);
    }
    @Test(expected = IOException.class) public void incompletePolylineIsRejected() throws Exception { WalkingRoutePlanner.decodePolyline6("_"); }
    @Test(expected = IOException.class) public void failedRouteResponseIsRejected() throws Exception { WalkingRoutePlanner.parse(new JSONObject("{\"error\":\"No suitable edges\"}")); }
    @Test(expected = IllegalArgumentException.class) public void nonHttpsServiceIsRejected() throws Exception { WalkingRoutePlanner.validateEndpoint("http://route.example/route"); }
}
