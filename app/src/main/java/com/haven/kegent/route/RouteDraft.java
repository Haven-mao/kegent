package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class RouteDraft {
    public enum Mode { ROAD, TRACK, MANUAL }
    public static final class Anchor {
        public RunRoute.Point point;
        public boolean manual;
        public Anchor(RunRoute.Point point) { this.point = point; }
    }
    public Mode mode = Mode.ROAD;
    public String name = "地图路线";
    public final List<Anchor> anchors = new ArrayList<>();
    public boolean closed, smooth, closingManual, trackInitialized;
    public RunRoute.Point center = new RunRoute.Point(40.6869, 122.1168);
    public double radius = 36.8, straight = (400 - 2 * Math.PI * 36.8) / 2;
    public double bearing, startFraction, laneWidth = 1.22;
    public int lane = 1;
    public boolean clockwise;

    public List<RunRoute.Point> points() {
        List<RunRoute.Point> result = new ArrayList<>();
        for (Anchor anchor : anchors) result.add(anchor.point);
        return result;
    }
    public int segmentCount() { return Math.max(0, anchors.size() - 1) + (closed && anchors.size() > 1 ? 1 : 0); }
    public boolean manualSegment(int index) {
        return index == anchors.size() - 1 ? closingManual : anchors.get(index + 1).manual;
    }
    public static JSONArray encodePoints(List<RunRoute.Point> points) throws Exception {
        JSONArray result = new JSONArray();
        for (RunRoute.Point p : points) result.put(new JSONArray().put(p.longitude).put(p.latitude));
        return result;
    }
    public static List<RunRoute.Point> decodePoints(JSONArray data) throws Exception {
        if (data.length() > 50000) throw new IllegalArgumentException("路线最多支持 50,000 个点");
        List<RunRoute.Point> result = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONArray p = data.getJSONArray(i);
            result.add(new RunRoute.Point(p.getDouble(1), p.getDouble(0)));
        }
        return result;
    }
    public JSONObject json() throws Exception {
        JSONObject o = new JSONObject().put("version", 1).put("crs", "WGS84").put("mode", mode.name())
                .put("name", name).put("closed", closed).put("smooth", smooth).put("closingManual", closingManual).put("trackInitialized", trackInitialized)
                .put("center", new JSONArray().put(center.longitude).put(center.latitude))
                .put("radius", radius).put("straight", straight).put("bearing", bearing)
                .put("startFraction", startFraction).put("lane", lane).put("laneWidth", laneWidth).put("clockwise", clockwise);
        JSONArray a = new JSONArray();
        for (Anchor anchor : anchors) a.put(new JSONObject().put("lon", anchor.point.longitude)
                .put("lat", anchor.point.latitude).put("manual", anchor.manual));
        return o.put("anchors", a);
    }
    public static RouteDraft fromJson(JSONObject o) throws Exception {
        if (o.getInt("version") != 1 || !"WGS84".equals(o.getString("crs")))
            throw new IllegalArgumentException("不支持的路线草稿格式或坐标系");
        RouteDraft d = new RouteDraft();
        d.mode = Mode.valueOf(o.getString("mode")); d.name = o.optString("name", "地图路线");
        d.trackInitialized = o.optBoolean("trackInitialized", d.mode == Mode.TRACK);
        d.closed = o.optBoolean("closed"); d.smooth = o.optBoolean("smooth"); d.closingManual = o.optBoolean("closingManual");
        JSONArray center = o.getJSONArray("center"); d.center = new RunRoute.Point(center.getDouble(1), center.getDouble(0));
        if (Math.abs(d.center.latitude) > 85) throw new IllegalArgumentException("编辑地图暂不支持极地区域");
        d.radius = o.getDouble("radius"); d.straight = o.getDouble("straight"); d.bearing = o.getDouble("bearing");
        d.startFraction = o.getDouble("startFraction"); d.lane = o.getInt("lane"); d.laneWidth = o.getDouble("laneWidth");
        d.clockwise = o.optBoolean("clockwise");
        JSONArray anchors = o.getJSONArray("anchors");
        if (anchors.length() > 500) throw new IllegalArgumentException("编辑控制点不能超过 500 个");
        for (int i = 0; i < anchors.length(); i++) {
            JSONObject a = anchors.getJSONObject(i);
            Anchor anchor = new Anchor(new RunRoute.Point(a.getDouble("lat"), a.getDouble("lon")));
            anchor.manual = a.optBoolean("manual"); d.anchors.add(anchor);
        }
        if (d.mode == Mode.ROAD && d.anchors.size() > 20) throw new IllegalArgumentException("道路模式最多支持 20 个选点");
        return d;
    }
    public static RouteDraft fromRoute(RunRoute route) {
        RouteDraft d = new RouteDraft(); d.mode = Mode.MANUAL; d.name = route.name; d.closed = route.closed;
        List<RunRoute.Point> points = RouteGeometry.simplify(route, 1);
        if (points.size() > 500) throw new IllegalArgumentException("当前路线过于复杂，请新建地图路线");
        if (d.closed && points.size() > 2) points.remove(points.size() - 1);
        for (RunRoute.Point p : points) d.anchors.add(new Anchor(p));
        d.center = route.get(0); return d;
    }
}
