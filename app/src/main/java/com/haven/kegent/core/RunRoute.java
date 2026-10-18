package com.haven.kegent.core;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/** Immutable route geometry shared by live playback and historical records. */
public final class RunRoute implements Serializable {
    public static final class Point implements Serializable {
        public final double latitude, longitude;

        public Point(double latitude, double longitude) {
            if (!Double.isFinite(latitude)
                    || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90
                    || Math.abs(longitude) > 180) {
                throw new IllegalArgumentException("路线包含无效经纬度");
            }
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private final Point[] points;
    private final double[] cumulative;
    public final String name;
    public final double lengthMeters;
    public final boolean closed;

    public RunRoute(String name, List<Point> source) {
        if (source.size() > 50000) throw new IllegalArgumentException("路线最多支持 50,000 个点");
        List<Point> unique = new ArrayList<>();
        for (Point p : source) {
            if (unique.isEmpty() || distance(unique.get(unique.size() - 1), p) > 0.05)
                unique.add(p);
        }
        if (unique.size() < 2) throw new IllegalArgumentException("路线至少需要两个不同坐标点");
        points = unique.toArray(new Point[0]);
        cumulative = new double[points.length];
        for (int i = 1; i < points.length; i++)
            cumulative[i] = cumulative[i - 1] + distance(points[i - 1], points[i]);
        lengthMeters = cumulative[cumulative.length - 1];
        if (!Double.isFinite(lengthMeters) || lengthMeters < 1)
            throw new IllegalArgumentException("路线长度不足 1 米");
        this.name = name;
        closed = distance(points[0], points[points.length - 1]) < 0.5;
    }

    public static RunRoute readKml(InputStream input, String name) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) != -1) {
            if (buffer.size() + read > 4 * 1024 * 1024)
                throw new IllegalArgumentException("KML 文件不能超过 4 MB");
            buffer.write(chunk, 0, read);
        }
        byte[] xml = buffer.toByteArray();
        for (byte value : xml)
            if (value == 0) throw new IllegalArgumentException("请将 KML 保存为 UTF-8 编码");
        String declarations =
                new String(xml, java.nio.charset.StandardCharsets.UTF_8)
                        .toUpperCase(java.util.Locale.ROOT);
        if (declarations.contains("<!DOCTYPE") || declarations.contains("<!ENTITY"))
            throw new IllegalArgumentException("KML 不支持 DTD 或实体声明");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (javax.xml.parsers.ParserConfigurationException ignored) {
            /* DTD and entity declarations are rejected above. */
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (javax.xml.parsers.ParserConfigurationException ignored) {
            /* Android parser may not expose SAX features. */
        }
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (javax.xml.parsers.ParserConfigurationException ignored) {
            /* Android parser uses disabled external entities. */
        }
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver(
                (publicId, systemId) -> {
                    throw new org.xml.sax.SAXException("不支持外部 XML 实体");
                });
        Document doc = builder.parse(new java.io.ByteArrayInputStream(xml));
        NodeList lines = doc.getElementsByTagNameNS("*", "LineString");
        List<Point> best = new ArrayList<>();
        for (int i = 0; i < lines.getLength(); i++) {
            org.w3c.dom.Element line = (org.w3c.dom.Element) lines.item(i);
            NodeList coordinates = line.getElementsByTagNameNS("*", "coordinates");
            if (coordinates.getLength() > 0) {
                List<Point> candidate = parsePoints(coordinates.item(0).getTextContent());
                if (candidate.size() > best.size()) best = candidate;
            }
        }
        if (best.isEmpty()) {
            NodeList coordinates = doc.getElementsByTagNameNS("*", "coordinates");
            if (coordinates.getLength() > 0)
                best = parsePoints(coordinates.item(0).getTextContent());
        }
        return new RunRoute(name, best);
    }

    private static List<Point> parsePoints(String text) {
        List<Point> result = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            String[] values = token.split(",");
            if (values.length < 2) continue;
            if (result.size() >= 50000) throw new IllegalArgumentException("路线点数过多");
            result.add(new Point(Double.parseDouble(values[1]), Double.parseDouble(values[0])));
        }
        return result;
    }

    public int size() {
        return points.length;
    }

    public Point get(int i) {
        return points[i];
    }

    public double distanceAt(int i) {
        return cumulative[i];
    }

    /** Open routes reverse at the endpoint instead of teleporting back to the start. */
    public double routePosition(double traveled) {
        double period = lengthMeters * (closed ? 1 : 2);
        double position = Math.max(0, traveled) % period;
        return position > lengthMeters ? period - position : position;
    }

    public Point at(double traveled) {
        double position = routePosition(traveled);
        int index = Arrays.binarySearch(cumulative, position);
        if (index >= 0) return points[index];
        index = -index - 1;
        if (index <= 0) return points[0];
        if (index >= points.length) return points[points.length - 1];
        double fraction =
                (position - cumulative[index - 1]) / (cumulative[index] - cumulative[index - 1]);
        Point a = points[index - 1], b = points[index];
        double delta = b.longitude - a.longitude;
        if (delta > 180) delta -= 360;
        if (delta < -180) delta += 360;
        double longitude = a.longitude + delta * fraction;
        if (longitude > 180) longitude -= 360;
        if (longitude < -180) longitude += 360;
        return new Point(a.latitude + (b.latitude - a.latitude) * fraction, longitude);
    }

    public float bearingAt(double traveled) {
        Point a = at(traveled), b = at(traveled + 1);
        double phi1 = Math.toRadians(a.latitude), phi2 = Math.toRadians(b.latitude);
        double delta = Math.toRadians(b.longitude - a.longitude);
        return (float)
                ((Math.toDegrees(
                                        Math.atan2(
                                                Math.sin(delta) * Math.cos(phi2),
                                                Math.cos(phi1) * Math.sin(phi2)
                                                        - Math.sin(phi1)
                                                                * Math.cos(phi2)
                                                                * Math.cos(delta)))
                                + 360)
                        % 360);
    }

    public static double distance(Point p1, Point p2) {
        double a = 6378137, f = 1 / 298.257223563, b = (1 - f) * a;
        double l = Math.toRadians(p2.longitude - p1.longitude);
        double u1 = Math.atan((1 - f) * Math.tan(Math.toRadians(p1.latitude)));
        double u2 = Math.atan((1 - f) * Math.tan(Math.toRadians(p2.latitude)));
        double su1 = Math.sin(u1), cu1 = Math.cos(u1), su2 = Math.sin(u2), cu2 = Math.cos(u2);
        double lambda = l, sinSigma = 0, cosSigma = 0, sigma = 0, cosSqAlpha = 0, cos2SigmaM = 0;
        for (int i = 0; i < 200; i++) {
            double sl = Math.sin(lambda), cl = Math.cos(lambda);
            sinSigma = Math.hypot(cu2 * sl, cu1 * su2 - su1 * cu2 * cl);
            if (sinSigma == 0) return 0;
            cosSigma = su1 * su2 + cu1 * cu2 * cl;
            sigma = Math.atan2(sinSigma, cosSigma);
            double sinAlpha = cu1 * cu2 * sl / sinSigma;
            cosSqAlpha = 1 - sinAlpha * sinAlpha;
            cos2SigmaM = cosSqAlpha < 1e-15 ? 0 : cosSigma - 2 * su1 * su2 / cosSqAlpha;
            double c = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha));
            double previous = lambda;
            lambda =
                    l
                            + (1 - c)
                                    * f
                                    * sinAlpha
                                    * (sigma
                                            + c
                                                    * sinSigma
                                                    * (cos2SigmaM
                                                            + c
                                                                    * cosSigma
                                                                    * (-1
                                                                            + 2
                                                                                    * cos2SigmaM
                                                                                    * cos2SigmaM)));
            if (Math.abs(lambda - previous) <= 1e-12) {
                double u = cosSqAlpha * (a * a - b * b) / (b * b);
                double aa = 1 + u / 16384 * (4096 + u * (-768 + u * (320 - 175 * u)));
                double bb = u / 1024 * (256 + u * (-128 + u * (74 - 47 * u)));
                double delta =
                        bb
                                * sinSigma
                                * (cos2SigmaM
                                        + bb
                                                / 4
                                                * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)
                                                        - bb
                                                                / 6
                                                                * cos2SigmaM
                                                                * (-3 + 4 * sinSigma * sinSigma)
                                                                * (-3
                                                                        + 4
                                                                                * cos2SigmaM
                                                                                * cos2SigmaM)));
                return b * aa * (sigma - delta);
            }
        }
        double dLat = Math.toRadians(p2.latitude - p1.latitude),
                dLon = Math.toRadians(p2.longitude - p1.longitude);
        double h =
                Math.pow(Math.sin(dLat / 2), 2)
                        + Math.cos(Math.toRadians(p1.latitude))
                                * Math.cos(Math.toRadians(p2.latitude))
                                * Math.pow(Math.sin(dLon / 2), 2);
        return 6371008.8 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
    }
}
