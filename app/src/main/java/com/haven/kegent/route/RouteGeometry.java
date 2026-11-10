package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Geometry is generated in a local, ellipsoid-scaled metre grid, then stored as WGS84. */
public final class RouteGeometry {
    private RouteGeometry() {}

    public static final class Plane {
        private final RunRoute.Point origin;
        private final double northScale, eastScale;
        public Plane(RunRoute.Point origin) {
            this.origin = origin;
            double phi = Math.toRadians(origin.latitude);
            double factor = 1 - 0.00669437999014 * Math.sin(phi) * Math.sin(phi);
            northScale = Math.PI / 180 * 6378137 * (1 - 0.00669437999014) / Math.pow(factor, 1.5);
            eastScale = Math.PI / 180 * 6378137 / Math.sqrt(factor) * Math.cos(phi);
            if (Math.abs(eastScale) < 100) throw new IllegalArgumentException("暂不支持极地路线");
        }
        public double[] xy(RunRoute.Point p) {
            double d = p.longitude - origin.longitude;
            if (d > 180) d -= 360;
            if (d < -180) d += 360;
            return new double[] {d * eastScale, (p.latitude - origin.latitude) * northScale};
        }
        public RunRoute.Point point(double x, double y) {
            double lon = origin.longitude + x / eastScale;
            lon = ((lon + 540) % 360) - 180;
            return new RunRoute.Point(origin.latitude + y / northScale, lon);
        }
    }

    public static RunRoute.Point offset(RunRoute.Point center, double along, double across, double bearing) {
        double angle = Math.toRadians(bearing);
        return new Plane(center).point(along * Math.sin(angle) + across * Math.cos(angle),
                along * Math.cos(angle) - across * Math.sin(angle));
    }

    public static RunRoute.Point center(RunRoute route) {
        Plane plane = new Plane(route.get(0)); double x = 0, y = 0;
        for (int i = 0; i < 256; i++) { double[] p = plane.xy(route.at(route.lengthMeters * i / 256)); x += p[0]; y += p[1]; }
        return plane.point(x / 256, y / 256);
    }

    /** Fit only an existing stadium-shaped closed route, never infer a track from its first point. */
    public static RouteDraft fitTrack(RunRoute route) {
        if (!route.closed || route.lengthMeters < 60 || route.lengthMeters > 7000) return null;
        Plane plane = new Plane(center(route)); List<double[]> samples = new ArrayList<>();
        double xx = 0, xy = 0, yy = 0;
        for (int i = 0; i < 512; i++) {
            double[] p = plane.xy(route.at(route.lengthMeters * i / 512)); samples.add(p);
            xx += p[0] * p[0]; xy += p[0] * p[1]; yy += p[1] * p[1];
        }
        double angle = .5 * Math.atan2(2 * xy, xx - yy), ux = Math.cos(angle), uy = Math.sin(angle);
        double minA = Double.MAX_VALUE, maxA = -Double.MAX_VALUE, minB = Double.MAX_VALUE, maxB = -Double.MAX_VALUE;
        for (double[] p : samples) {
            double a = p[0] * ux + p[1] * uy, b = -p[0] * uy + p[1] * ux;
            minA = Math.min(minA, a); maxA = Math.max(maxA, a); minB = Math.min(minB, b); maxB = Math.max(maxB, b);
        }
        double radius = (maxB - minB) / 2, straight = maxA - minA - 2 * radius;
        if (radius < 2 || radius > 500 || straight < 5 || straight > 2000) return null;
        double midA = (maxA + minA) / 2, midB = (maxB + minB) / 2, error = 0, maxError = 0;
        for (double[] p : samples) {
            double a = p[0] * ux + p[1] * uy - midA, b = -p[0] * uy + p[1] * ux - midB;
            double deviation = Math.abs(Math.hypot(Math.max(0, Math.abs(a) - straight / 2), b) - radius);
            error += deviation * deviation; maxError = Math.max(maxError, deviation);
        }
        if (Math.sqrt(error / samples.size()) > 4 || maxError > 10) return null;
        RouteDraft fitted = new RouteDraft(); fitted.mode = RouteDraft.Mode.TRACK; fitted.trackInitialized = true;
        fitted.name = "操场路线"; fitted.radius = radius; fitted.straight = straight;
        fitted.center = plane.point(midA * ux - midB * uy, midA * uy + midB * ux);
        fitted.bearing = (90 - Math.toDegrees(angle) + 360) % 180;
        RunRoute template = new RunRoute("fit", track(fitted)); int nearest = 0; double distance = Double.MAX_VALUE;
        for (int i = 0; i < template.size(); i++) {
            double delta = RunRoute.distance(route.get(0), template.get(i)); if (delta < distance) { distance = delta; nearest = i; }
        }
        fitted.startFraction = template.distanceAt(nearest) / template.lengthMeters;
        return fitted;
    }

    public static List<RunRoute.Point> track(RouteDraft d) {
        double radius = d.radius + (d.lane - 1) * d.laneWidth;
        if (!Double.isFinite(d.straight) || !Double.isFinite(radius) || d.straight < 1 || d.straight > 2000
                || d.radius < 2 || radius > 500 || d.lane < 1 || d.lane > 9
                || !Double.isFinite(d.laneWidth) || d.laneWidth < .5 || d.laneWidth > 3
                || !Double.isFinite(d.bearing) || !Double.isFinite(d.startFraction))
            throw new IllegalArgumentException("跑道尺寸无效：直道 1–2000 米，半径 2–500 米，道宽 0.5–3 米");
        double length = 2 * d.straight + 2 * Math.PI * radius;
        int count = (int) Math.ceil(length / 2);
        List<RunRoute.Point> points = new ArrayList<>();
        // Include every straight/arc junction as well as regular arc-length samples.
        List<Double> distances = new ArrayList<>();
        double phase = ((d.startFraction % 1) + 1) % 1 * length;
        for (int i = 0; i < count; i++) distances.add(length * i / count);
        for (double junction : new double[] {0, d.straight, d.straight + Math.PI * radius,
                2 * d.straight + Math.PI * radius}) {
            double relative = d.clockwise ? phase - junction : junction - phase;
            distances.add((relative % length + length) % length);
        }
        Collections.sort(distances);
        for (double distance : distances) {
            double s = ((phase + (d.clockwise ? -distance : distance)) % length + length) % length;
            double x, y;
            if (s < d.straight) { x = -d.straight / 2 + s; y = radius; }
            else if ((s -= d.straight) < Math.PI * radius) {
                double a = Math.PI / 2 - s / radius;
                x = d.straight / 2 + radius * Math.cos(a); y = radius * Math.sin(a);
            } else if ((s -= Math.PI * radius) < d.straight) {
                x = d.straight / 2 - s; y = -radius;
            } else {
                s -= d.straight;
                double a = -Math.PI / 2 - s / radius;
                x = -d.straight / 2 + radius * Math.cos(a); y = radius * Math.sin(a);
            }
            // Local x follows the long axis; y is to its right.
            points.add(offset(d.center, x, y, d.bearing));
        }
        if (!points.isEmpty()) points.add(points.get(0));
        return points;
    }

    public static List<RunRoute.Point> manual(List<RunRoute.Point> input, boolean closed, boolean smooth) {
        List<RunRoute.Point> clean = new ArrayList<>();
        for (RunRoute.Point p : input)
            if (clean.isEmpty() || RunRoute.distance(clean.get(clean.size() - 1), p) > .1) clean.add(p);
        if (closed && clean.size() > 2 && RunRoute.distance(clean.get(0), clean.get(clean.size() - 1)) < .1)
            clean.remove(clean.size() - 1);
        if (clean.size() < 2) return clean;
        if (!smooth || clean.size() < 3) {
            if (closed) clean.add(clean.get(0));
            return clean;
        }
        Plane plane = new Plane(clean.get(0));
        int size = clean.size(), segments = closed ? size : size - 1;
        List<RunRoute.Point> result = new ArrayList<>();
        for (int i = 0; i < segments; i++) {
            double[] p1 = plane.xy(clean.get(i));
            double[] p2 = plane.xy(clean.get((i + 1) % size));
            double[] p0 = i > 0 ? plane.xy(clean.get(i - 1)) : closed ? plane.xy(clean.get(size - 1))
                    : new double[] {2 * p1[0] - p2[0], 2 * p1[1] - p2[1]};
            double[] p3 = i + 2 < size ? plane.xy(clean.get(i + 2)) : closed ? plane.xy(clean.get((i + 2) % size))
                    : new double[] {2 * p2[0] - p1[0], 2 * p2[1] - p1[1]};
            int steps = Math.max(2, (int) Math.ceil(Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) / 3));
            if (steps > 10000 || result.size() + steps > 49000)
                throw new IllegalArgumentException("手动曲线过长，请缩短路线或关闭平滑");
            double t0 = 0, t1 = knot(t0, p0, p1), t2 = knot(t1, p1, p2), t3 = knot(t2, p2, p3);
            for (int j = 0; j < steps; j++) {
                double t = t1 + (t2 - t1) * j / steps;
                double[] a1 = blend(p0, p1, t0, t1, t), a2 = blend(p1, p2, t1, t2, t), a3 = blend(p2, p3, t2, t3, t);
                double[] b1 = blend(a1, a2, t0, t2, t), b2 = blend(a2, a3, t1, t3, t);
                double[] c = blend(b1, b2, t1, t2, t);
                result.add(plane.point(c[0], c[1]));
            }
        }
        result.add(closed ? result.get(0) : clean.get(size - 1));
        return result;
    }
    private static double knot(double t, double[] a, double[] b) {
        return t + Math.max(.001, Math.sqrt(Math.hypot(a[0] - b[0], a[1] - b[1])));
    }
    private static double[] blend(double[] a, double[] b, double from, double to, double t) {
        double f = (t - from) / (to - from);
        return new double[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f};
    }

    public static List<RunRoute.Point> simplify(RunRoute route, double tolerance) {
        List<RunRoute.Point> points = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) points.add(route.get(i));
        boolean[] keep = new boolean[points.size()];
        keep[0] = keep[points.size() - 1] = true;
        Plane plane = new Plane(points.get(0));
        java.util.ArrayDeque<int[]> work = new java.util.ArrayDeque<>();
        work.push(new int[] {0, points.size() - 1});
        while (!work.isEmpty()) {
            int[] range = work.pop();
            double max = tolerance; int best = -1;
            double[] a = plane.xy(points.get(range[0])), b = plane.xy(points.get(range[1]));
            for (int i = range[0] + 1; i < range[1]; i++) {
                double distance = segmentDistance(plane.xy(points.get(i)), a, b);
                if (distance > max) { max = distance; best = i; }
            }
            if (best >= 0) {
                keep[best] = true; work.push(new int[] {range[0], best}); work.push(new int[] {best, range[1]});
            }
        }
        List<RunRoute.Point> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) if (keep[i]) result.add(points.get(i));
        return result;
    }
    public static double segmentDistance(double[] p, double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1], square = dx * dx + dy * dy;
        double f = square == 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / square));
        return Math.hypot(p[0] - a[0] - f * dx, p[1] - a[1] - f * dy);
    }
}
