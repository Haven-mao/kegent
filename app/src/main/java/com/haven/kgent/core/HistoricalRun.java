package com.haven.kgent.core;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

public final class HistoricalRun {
    public final RunRoute route;
    public final long startTime, endTime, durationSeconds;
    public final int paceSeconds, cadence;
    public final double distanceMeters, weightKg;
    public final String timezone;
    public final boolean naturalPace;
    private final PaceProfile profile;

    public HistoricalRun(
            RunRoute route,
            long startTime,
            long endTime,
            int paceSeconds,
            int cadence,
            double weightKg,
            String timezone,
            long now) {
        this(route, startTime, endTime, paceSeconds, cadence, weightKg, timezone, now, true);
    }

    public HistoricalRun(
            RunRoute route,
            long startTime,
            long endTime,
            int paceSeconds,
            int cadence,
            double weightKg,
            String timezone,
            long now,
            boolean naturalPace) {
        if (route == null) throw new IllegalArgumentException("请先选择路线");
        if (startTime <= 0 || endTime <= startTime)
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        if (endTime > now) throw new IllegalArgumentException("请选择已经结束的历史时间段");
        if (startTime % 1000 != 0 || endTime % 1000 != 0)
            throw new IllegalArgumentException("起止时间必须精确到整秒");
        long duration = (endTime - startTime) / 1000;
        if (duration <= 20 || duration > 12 * 3600)
            throw new IllegalArgumentException("单次时长应大于 20 秒且不超过 12 小时");
        if (paceSeconds < 120 || paceSeconds > 1200)
            throw new IllegalArgumentException("配速应在 2:00 至 20:00 分/公里之间");
        if (cadence < 60 || cadence > 240)
            throw new IllegalArgumentException("步频应在每分钟 60 至 240 步之间");
        if (!Double.isFinite(weightKg) || weightKg < 20 || weightKg > 300)
            throw new IllegalArgumentException("体重应在 20 至 300 kg 之间");
        ZoneId.of(timezone);
        double distance = duration * 1000.0 / paceSeconds;
        if (distance <= 100 || distance > 200000)
            throw new IllegalArgumentException("计算距离应大于 100 米且不超过 200 公里");
        this.route = route;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = duration;
        this.paceSeconds = paceSeconds;
        this.cadence = cadence;
        this.distanceMeters = distance;
        this.weightKg = weightKg;
        this.timezone = timezone;
        this.naturalPace = naturalPace;
        this.profile =
                new PaceProfile(
                        distance,
                        naturalPace,
                        startTime
                                ^ Long.rotateLeft(endTime, 19)
                                ^ Double.doubleToLongBits(route.get(0).latitude)
                                ^ Double.doubleToLongBits(route.get(0).longitude));
    }

    public static int parsePace(String value) {
        String[] parts = value.trim().split(":");
        if (parts.length != 2) throw new IllegalArgumentException("配速请填写为 分:秒，例如 5:30");
        try {
            int minutes = Integer.parseInt(parts[0]), seconds = Integer.parseInt(parts[1]);
            if (minutes < 0 || minutes > 20 || seconds < 0 || seconds > 59)
                throw new NumberFormatException();
            return minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("配速格式无效，例如 5:30");
        }
    }

    public static String formatPace(double speed) {
        if (speed <= 0) return "--:--";
        long seconds = Math.round(1000 / speed);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    public int sampleIntervalSeconds() {
        return Math.max(1, (int) Math.ceil(durationSeconds / 15000.0));
    }

    public int sampleCount() {
        return (int) Math.ceil(durationSeconds / (double) sampleIntervalSeconds()) + 1;
    }

    public double speed() {
        return 1000.0 / paceSeconds;
    }

    public double distanceAtSecond(double second) {
        return profile.distanceAt(Math.max(0, Math.min(durationSeconds, second)) * speed());
    }

    public double speedAtSecond(double second) {
        return speed() * profile.speedFactorAt(distanceAtSecond(second));
    }

    public double maximumSpeed() {
        return speed() * profile.maxSpeedFactor();
    }

    public double secondsAtDistance(double meters) {
        return profile.effortAt(meters) / speed();
    }

    public int totalSteps() {
        return (int) Math.round(durationSeconds * cadence / 60.0);
    }

    public int calories() {
        return (int) Math.round(weightKg * distanceMeters / 1000.0);
    }

    public String localDay() {
        return Instant.ofEpochMilli(startTime).atZone(ZoneId.of(timezone)).toLocalDate().toString();
    }
}
