package com.haven.kegent;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.haven.kegent.core.RunEngine;
import com.haven.kegent.core.RunRoute;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class LocationServiceSmokeTest {
    @Test
    public void providerAndPauseLifecycle() throws Exception {
        Assume.assumeTrue(
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("kegentMockFixture")));
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            Intent start =
                    new Intent(c, LocationMockService.class)
                            .setAction(LocationMockService.START)
                            .putExtra("speed", 3.0)
                            .putExtra("natural_pace", false)
                            .putExtra("repeats", 15)
                            .putExtra("route_name", "DUTPJ");
            ContextCompat.startForegroundService(c, start);
            for (int i = 0; i < 30 && !LocationMockService.latest.active(); i++) Thread.sleep(100);
            assertEquals(
                    LocationMockService.lastError,
                    RunEngine.State.RUNNING,
                    LocationMockService.latest.state);
            Thread.sleep(700);
            LocationManager manager =
                    (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            assertNotNull(location);
            assertEquals(3, location.getSpeed(), 0.01);
            RunRoute route;
            try (InputStream in = c.getResources().openRawResource(R.raw.dutpj_mutipoint)) {
                route = RunRoute.readKml(in, "fixture");
            }
            assertTrue(
                    RunRoute.distance(
                                    route.get(0),
                                    new RunRoute.Point(
                                            location.getLatitude(), location.getLongitude()))
                            < 15);
            c.startService(
                    new Intent(c, LocationMockService.class).setAction(LocationMockService.PAUSE));
            Thread.sleep(150);
            double distance = LocationMockService.latest.distance;
            Thread.sleep(500);
            assertEquals(RunEngine.State.PAUSED, LocationMockService.latest.state);
            assertEquals(distance, LocationMockService.latest.distance, 0.001);
            c.startService(
                    new Intent(c, LocationMockService.class).setAction(LocationMockService.RESUME));
            Thread.sleep(500);
            assertTrue(LocationMockService.latest.distance > distance);
        } finally {
            c.stopService(new Intent(c, LocationMockService.class));
            Thread.sleep(200);
            assertFalse(LocationMockService.latest.active());
        }
    }
}
