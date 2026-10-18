package com.haven.kegent.keep;

import static org.junit.Assert.*;

import com.haven.kegent.core.HistoricalRun;
import com.haven.kegent.core.RunRoute;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class KeepRecordBuilderTest {
    private HistoricalRun run() {
        return new HistoricalRun(
                new RunRoute(
                        "route",
                        Arrays.asList(
                                new RunRoute.Point(30, 120), new RunRoute.Point(30.001, 120))),
                1700000000000L,
                1700001800000L,
                360,
                170,
                65,
                "Asia/Shanghai",
                1800000000000L);
    }

    @Test
    public void timestampsAndFinalTotalsAgree() throws Exception {
        HistoricalRun run = run();
        JSONObject data = KeepRecordBuilder.build(run, "test-user");
        JSONArray points = data.getJSONArray("geoPoints");
        assertEquals("outdoorRunning", data.getString("dataType"));
        assertEquals("keep_app", data.getString("source"));
        assertEquals("keep_app", data.getString("source_page"));
        assertFalse(data.getBoolean("isUploaded"));
        assertTrue(data.getBoolean("offline"));
        assertEquals(run.sampleCount(), points.length());
        long previous = -1;
        for (int i = 0; i < points.length(); i++) {
            JSONObject p = points.getJSONObject(i);
            long t = p.getLong("unixTimestamp");
            assertTrue(t > previous);
            assertEquals(t - run.startTime, p.getLong("timestamp"));
            previous = t;
        }
        JSONObject end = points.getJSONObject(points.length() - 1);
        assertEquals(run.endTime, end.getLong("unixTimestamp"));
        assertEquals(run.distanceMeters, end.getDouble("currentTotalDistance"), 0.0001);
        assertEquals(run.totalSteps(), end.getInt("currentTotalSteps"));
    }

    @Test
    public void logIdentityIsStableForTheSameAccountAndEndTime() {
        HistoricalRun run = run();
        assertEquals(
                "test-user_" + (Long.MAX_VALUE - run.endTime) + "_rn",
                KeepRecordBuilder.logId("test-user", run.endTime));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidAccountCannotBecomeAPath() {
        KeepRecordBuilder.logId("../escape", 1700000000000L);
    }

    @Test
    public void sqlQuotesDoNotAllowStringBreakout() {
        assertEquals("'a''b'", KeepRecordBuilder.quote("a'b"));
    }

    @Test
    public void secondsVariableSpeedAndMapCoordinatesAreConsistent() throws Exception {
        HistoricalRun template = run();
        HistoricalRun run =
                new HistoricalRun(
                        template.route,
                        1700000037000L,
                        1700001804000L,
                        330,
                        170,
                        65,
                        "Asia/Shanghai",
                        1800000000000L);
        JSONObject data = KeepRecordBuilder.build(run, "test-user");
        JSONArray points = data.getJSONArray("geoPoints");
        JSONObject last = points.getJSONObject(points.length() - 1);
        assertEquals(run.endTime, last.getLong("unixTimestamp"));
        assertEquals(1767, last.getInt("currentTotalDuration"));
        assertEquals(run.distanceMeters, last.getDouble("currentTotalDistance"), 1e-7);
        assertTrue(data.getDouble("maxSpeed") > data.getDouble("averageSpeed"));
        assertNotEquals(
                points.getJSONObject(0).getDouble("speed"),
                points.getJSONObject(1000).getDouble("speed"),
                0.01);
        for (int i = 1; i < points.length(); i++) {
            JSONObject a = points.getJSONObject(i - 1), b = points.getJSONObject(i);
            double distance =
                    b.getDouble("currentTotalDistance") - a.getDouble("currentTotalDistance");
            double dt = (b.getLong("unixTimestamp") - a.getLong("unixTimestamp")) / 1000.0;
            assertEquals(b.getDouble("speed") / 3.6, distance / dt, 0.003);
            assertEquals(Math.round(3600 / b.getDouble("speed")), b.getLong("currentPace"));
        }
        RunRoute.Point mapped = KeepCoordinates.fromGps(run.route.at(0));
        assertEquals(mapped.latitude, points.getJSONObject(0).getDouble("latitude"), 1e-9);
        assertEquals(mapped.longitude, points.getJSONObject(0).getDouble("longitude"), 1e-9);
    }
}
