package com.haven.kegent.core;

/** A smooth distance-based pace curve, normalized to preserve the requested overall average. */
public final class PaceProfile {
    private final double total, step;
    private final double[] pace, effort;
    private final double maxSpeedFactor;

    public PaceProfile(double totalMeters, boolean natural, long seed) {
        if (!Double.isFinite(totalMeters) || totalMeters <= 0 || totalMeters > 200000)
            throw new IllegalArgumentException("路线距离无效");
        total = totalMeters;
        int count = natural ? Math.max(1, (int) Math.ceil(total / 5)) : 1;
        step = total / count;
        pace = new double[count + 1];
        effort = new double[count + 1];
        for (int i = 0; i <= count; i++) {
            double distance = i * step;
            double front = total > 1000 ? 1 - smooth((distance - 600) / 400) : 0;
            double phase = (seed & 0xffffL) / 65535.0 * Math.PI * 2;
            double variation =
                    0.58 * noise(distance / 280, seed)
                            + 0.25 * noise(distance / 700, seed ^ 0x6a09e667f3bcc909L)
                            + 0.17 * Math.sin(distance / 1600.0 * 2.4 + phase);
            // Use wider, low-frequency changes so whole-second Keep splits visibly differ.
            pace[i] = natural ? 1 - 0.08 * front + (0.028 + 0.010 * front) * variation : 1;
            if (i > 0) effort[i] = effort[i - 1] + (pace[i - 1] + pace[i]) * 0.5 * step;
        }
        double mean = effort[count] / total;
        double minimum = Double.MAX_VALUE;
        for (int i = 0; i <= count; i++) {
            pace[i] /= mean;
            effort[i] /= mean;
            minimum = Math.min(minimum, pace[i]);
        }
        effort[count] = total;
        maxSpeedFactor = 1 / minimum;
    }

    /**
     * Equivalent uniform-speed distance; multiplying elapsed seconds by mean speed gives effort.
     */
    public double distanceAt(double equivalentMeters) {
        if (equivalentMeters <= 0) return 0;
        if (equivalentMeters >= total) return total;
        int low = 0, high = effort.length - 1;
        while (low + 1 < high) {
            int middle = (low + high) >>> 1;
            if (effort[middle] <= equivalentMeters) low = middle;
            else high = middle;
        }
        double e = equivalentMeters - effort[low];
        double slope = (pace[low + 1] - pace[low]) / step;
        double dx = 2 * e / (pace[low] + Math.sqrt(pace[low] * pace[low] + 2 * slope * e));
        return Math.min(total, low * step + Math.max(0, Math.min(step, dx)));
    }

    public double effortAt(double distance) {
        if (distance <= 0) return 0;
        if (distance >= total) return total;
        int i = Math.min(pace.length - 2, (int) (distance / step));
        double dx = distance - i * step;
        return effort[i] + pace[i] * dx + (pace[i + 1] - pace[i]) / step * dx * dx * 0.5;
    }

    public double speedFactorAt(double distance) {
        double bounded = Math.max(0, Math.min(total, distance));
        int i = Math.min(pace.length - 2, (int) (bounded / step));
        return 1 / (pace[i] + (pace[i + 1] - pace[i]) * (bounded - i * step) / step);
    }

    public double maxSpeedFactor() {
        return maxSpeedFactor;
    }

    private static double smooth(double value) {
        double x = Math.max(0, Math.min(1, value));
        return x * x * (3 - 2 * x);
    }

    private static double noise(double position, long seed) {
        long anchor = (long) Math.floor(position);
        double a = random(anchor, seed), b = random(anchor + 1, seed);
        return a + (b - a) * smooth(position - anchor);
    }

    private static double random(long anchor, long seed) {
        long x = seed + anchor * 0x9e3779b97f4a7c15L;
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        x ^= x >>> 31;
        return (x >>> 11) * 0x1.0p-53 * 2 - 1;
    }
}
