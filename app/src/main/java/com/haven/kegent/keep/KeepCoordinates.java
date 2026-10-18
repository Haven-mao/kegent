package com.haven.kegent.keep;

import com.haven.kegent.core.RunRoute;

/** KML/GPS uses WGS84; Keep's Tencent location provider stores GCJ-02 points. */
public final class KeepCoordinates {
    private static final double AXIS = 6378245.0;
    private static final double ECCENTRICITY = 0.006693421622965943;

    private KeepCoordinates() {}

    public static RunRoute.Point fromGps(RunRoute.Point point) {
        double lat = point.latitude, lon = point.longitude;
        if (lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271) return point;
        double x = lon - 105, y = lat - 35;
        double latitudeOffset =
                -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        latitudeOffset += (20 * Math.sin(6 * x * Math.PI) + 20 * Math.sin(2 * x * Math.PI)) * 2 / 3;
        latitudeOffset += (20 * Math.sin(y * Math.PI) + 40 * Math.sin(y / 3 * Math.PI)) * 2 / 3;
        latitudeOffset +=
                (160 * Math.sin(y / 12 * Math.PI) + 320 * Math.sin(y / 30 * Math.PI)) * 2 / 3;
        double longitudeOffset =
                300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        longitudeOffset +=
                (20 * Math.sin(6 * x * Math.PI) + 20 * Math.sin(2 * x * Math.PI)) * 2 / 3;
        longitudeOffset += (20 * Math.sin(x * Math.PI) + 40 * Math.sin(x / 3 * Math.PI)) * 2 / 3;
        longitudeOffset +=
                (150 * Math.sin(x / 12 * Math.PI) + 300 * Math.sin(x / 30 * Math.PI)) * 2 / 3;
        double radians = Math.toRadians(lat);
        double magic = 1 - ECCENTRICITY * Math.sin(radians) * Math.sin(radians);
        double root = Math.sqrt(magic);
        latitudeOffset *= 180 / ((AXIS * (1 - ECCENTRICITY)) / (magic * root) * Math.PI);
        longitudeOffset *= 180 / (AXIS / root * Math.cos(radians) * Math.PI);
        return new RunRoute.Point(lat + latitudeOffset, lon + longitudeOffset);
    }
}
