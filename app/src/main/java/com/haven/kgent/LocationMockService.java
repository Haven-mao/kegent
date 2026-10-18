package com.haven.kgent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import com.haven.kgent.core.RunEngine;
import com.haven.kgent.core.RunRoute;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Locale;

/** Foreground owner of the run; an Activity recreation cannot create another ticker. */
public class LocationMockService extends Service {
    public static final String START = "start",
            PAUSE = "pause",
            RESUME = "resume",
            STOP = "stop",
            UPDATE = "update";
    public static volatile RunEngine.Snapshot latest =
            new RunEngine.Snapshot(RunEngine.State.READY, 0, 0, 0, 0);
    public static volatile String lastError = "";
    public static volatile RunRoute activeRoute;
    private final RunEngine engine = new RunEngine();
    private final Handler handler = new Handler();
    private LocationManager locations;
    private PowerManager.WakeLock wakeLock;
    private final java.util.Set<String> createdProviders = new java.util.HashSet<>();
    private double altitude = 0, accuracy = 5;
    private long interval = 300, lastNotification;
    private final Runnable ticker =
            new Runnable() {
                @Override
                public void run() {
                    RunEngine.Snapshot s = engine.tick(SystemClock.elapsedRealtime());
                    latest = s;
                    try {
                        if (s.state == RunEngine.State.RUNNING
                                || s.state == RunEngine.State.FINISHED) publish(s);
                        if (s.state == RunEngine.State.FINISHED) {
                            finishRun();
                            return;
                        }
                        if (SystemClock.elapsedRealtime() - lastNotification >= 5000) {
                            updateNotification();
                            lastNotification = SystemClock.elapsedRealtime();
                        }
                        if (s.active()) handler.postDelayed(this, interval);
                    } catch (Exception error) {
                        fail("定位写入失败，请检查模拟定位授权");
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        locations = (LocationManager) getSystemService(LOCATION_SERVICE);
        NotificationChannel channel =
                new NotificationChannel(
                        "kgent_run", "kgent 实时跑步", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        try {
            if (START.equals(action)) {
                if (engine.snapshot().active()) return START_NOT_STICKY;
                startForeground(31, notification());
                lastError = "";
                File selected = new File(getFilesDir(), "selected-route.kml");
                try (InputStream stream =
                        selected.exists()
                                ? new FileInputStream(selected)
                                : getResources().openRawResource(R.raw.dutpj_mutipoint)) {
                    activeRoute = RunRoute.readKml(stream, intent.getStringExtra("route_name"));
                }
                double speed = intent.getDoubleExtra("speed", 3);
                int repeats = intent.getIntExtra("repeats", 15);
                if (repeats < 1 || repeats > 500)
                    throw new IllegalArgumentException("次数应在 1 至 500 之间");
                altitude = intent.getDoubleExtra("altitude", 0);
                accuracy = intent.getDoubleExtra("accuracy", 5);
                interval = intent.getLongExtra("interval", 300);
                if (!Double.isFinite(altitude) || altitude < -500 || altitude > 10000)
                    throw new IllegalArgumentException("海拔值超出范围");
                if (!Double.isFinite(accuracy) || accuracy <= 0 || accuracy > 200)
                    throw new IllegalArgumentException("定位精度应在 0 至 200 米之间");
                if (interval < 100 || interval > 10000)
                    throw new IllegalArgumentException("更新间隔应在 0.1 至 10 秒之间");
                setupProviders();
                engine.start(
                        activeRoute.lengthMeters * repeats,
                        speed,
                        SystemClock.elapsedRealtime(),
                        intent.getBooleanExtra("natural_pace", true),
                        System.currentTimeMillis());
                wakeLock =
                        ((PowerManager) getSystemService(POWER_SERVICE))
                                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kgent:route");
                wakeLock.acquire(12 * 3600 * 1000L);
                handler.removeCallbacks(ticker);
                handler.post(ticker);
            } else if (PAUSE.equals(action)) {
                engine.pause(SystemClock.elapsedRealtime());
                latest = engine.snapshot();
                updateNotification();
            } else if (RESUME.equals(action)) {
                engine.resume(SystemClock.elapsedRealtime());
                latest = engine.snapshot();
                handler.removeCallbacks(ticker);
                handler.post(ticker);
                updateNotification();
            } else if (UPDATE.equals(action)) {
                engine.updateSpeed(
                        intent.getDoubleExtra("speed", 3), SystemClock.elapsedRealtime());
                latest = engine.snapshot();
                updateNotification();
            } else if (STOP.equals(action)) {
                engine.stop(SystemClock.elapsedRealtime());
                latest = engine.snapshot();
                finishRun();
            }
        } catch (SecurityException e) {
            fail("尚未获得模拟定位权限，请在开发者选项中选择 kgent");
        } catch (Exception e) {
            fail(e.getMessage() == null ? "无法启动运行" : e.getMessage());
        }
        return START_NOT_STICKY;
    }

    private void setupProviders() {
        for (String provider :
                new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                locations.removeTestProvider(provider);
            } catch (IllegalArgumentException ignored) {
            }
            boolean gps = LocationManager.GPS_PROVIDER.equals(provider);
            locations.addTestProvider(
                    provider,
                    !gps,
                    gps,
                    !gps,
                    !gps,
                    true,
                    true,
                    true,
                    gps
                            ? android.location.provider.ProviderProperties.POWER_USAGE_HIGH
                            : android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                    gps
                            ? android.location.provider.ProviderProperties.ACCURACY_FINE
                            : android.location.provider.ProviderProperties.ACCURACY_COARSE);
            createdProviders.add(provider);
            locations.setTestProviderEnabled(provider, true);
        }
    }

    private void publish(RunEngine.Snapshot snapshot) {
        RunRoute.Point point = activeRoute.at(snapshot.distance);
        for (String provider :
                new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            Location location = new Location(provider);
            location.setLatitude(point.latitude);
            location.setLongitude(point.longitude);
            location.setAltitude(altitude);
            location.setAccuracy((float) accuracy);
            location.setSpeed((float) snapshot.speed);
            location.setBearing(activeRoute.bearingAt(snapshot.distance));
            location.setTime(System.currentTimeMillis());
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            if (LocationManager.GPS_PROVIDER.equals(provider)) {
                android.os.Bundle extras = new android.os.Bundle();
                extras.putInt("satellites", 7);
                location.setExtras(extras);
            }
            locations.setTestProviderLocation(provider, location);
        }
    }

    private Notification notification() {
        RunEngine.Snapshot s = engine.snapshot();
        PendingIntent open =
                PendingIntent.getActivity(
                        this,
                        0,
                        new Intent(this, FakeGPSActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean paused = s.state == RunEngine.State.PAUSED;
        PendingIntent toggle =
                PendingIntent.getService(
                        this,
                        1,
                        new Intent(this, LocationMockService.class)
                                .setAction(paused ? RESUME : PAUSE),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop =
                PendingIntent.getService(
                        this,
                        2,
                        new Intent(this, LocationMockService.class).setAction(STOP),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, "kgent_run")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(paused ? "kgent · 已暂停" : "kgent · 路线运行中")
                .setContentText(
                        String.format(
                                Locale.CHINA,
                                "%.2f / %.2f km",
                                s.distance / 1000,
                                s.targetDistance / 1000))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, paused ? "继续" : "暂停", toggle)
                .addAction(0, "结束", stop)
                .build();
    }

    private void updateNotification() {
        getSystemService(NotificationManager.class).notify(31, notification());
    }

    private void fail(String message) {
        lastError = message;
        engine.fail();
        latest = engine.snapshot();
        finishRun();
    }

    private void finishRun() {
        handler.removeCallbacks(ticker);
        cleanupProviders();
        stopForeground(true);
        stopSelf();
    }

    private void cleanupProviders() {
        for (String p : createdProviders) {
            try {
                locations.removeTestProvider(p);
            } catch (Exception ignored) {
            }
        }
        createdProviders.clear();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        cleanupProviders();
        if (engine.snapshot().active()) {
            engine.stop(SystemClock.elapsedRealtime());
            latest = engine.snapshot();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
