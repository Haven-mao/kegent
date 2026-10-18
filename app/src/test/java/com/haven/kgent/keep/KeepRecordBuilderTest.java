package com.haven.kgent.keep;

import static org.junit.Assert.*;

import com.haven.kgent.core.HistoricalRun;
import com.haven.kgent.core.RunRoute;

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
}
