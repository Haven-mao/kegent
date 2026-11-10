package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** The same geometry is displayed, measured and saved. Incomplete road legs cannot be applied. */
public final class RouteAssembly {
    public final List<List<RunRoute.Point>> pieces = new ArrayList<>();
    public final List<List<RunRoute.Point>> pending = new ArrayList<>();
    public RunRoute route;
    public String problem = "";
    public double maxSnap;
    public static RouteAssembly build(RouteDraft d, Map<String, List<RunRoute.Point>> cache, String endpoint) {
        RouteAssembly out = new RouteAssembly();
        if (d.mode == RouteDraft.Mode.TRACK && !d.trackVisible) { out.problem = "点击地图放置操场路线"; return out; }
        try {
            if (d.mode != RouteDraft.Mode.ROAD) {
                List<RunRoute.Point> shape = d.mode == RouteDraft.Mode.TRACK ? RouteGeometry.track(d)
                        : RouteGeometry.manual(d.points(), d.closed, d.smooth);
                if (shape.size() < 2) { out.problem = "请在地图上至少选择两个不同位置"; return out; }
                out.pieces.add(shape); out.route = new RunRoute(d.name, shape); return out;
            }
            int count = d.segmentCount();
            if (count == 0) { out.problem = "点选起点和终点后自动规划步行路线"; return out; }
            List<List<RunRoute.Point>> legs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                RunRoute.Point a = d.anchors.get(i).point, b = d.anchors.get((i + 1) % d.anchors.size()).point;
                List<RunRoute.Point> shape = d.manualSegment(i) ? new ArrayList<>(Arrays.asList(a, b))
                        : cache.get(WalkingRoutePlanner.key(endpoint, a, b));
                if (shape == null) { out.pending.add(Arrays.asList(a, b)); legs.add(null); }
                else { legs.add(shape); if (!d.manualSegment(i)) out.maxSnap = Math.max(out.maxSnap,
                        Math.max(RunRoute.distance(a, shape.get(0)), RunRoute.distance(b, shape.get(shape.size() - 1)))); }
            }
            for (int i = 0; i < count; i++) {
                List<RunRoute.Point> leg = legs.get(i);
                if (leg == null || !d.manualSegment(i)) continue;
                int previous = (i + count - 1) % count, next = (i + 1) % count;
                if ((i > 0 || d.closed) && legs.get(previous) != null && !d.manualSegment(previous)) {
                    List<RunRoute.Point> p = legs.get(previous); leg.set(0, p.get(p.size() - 1));
                }
                if ((i < count - 1 || d.closed) && legs.get(next) != null && !d.manualSegment(next))
                    leg.set(leg.size() - 1, legs.get(next).get(0));
            }
            List<RunRoute.Point> all = new ArrayList<>();
            for (List<RunRoute.Point> leg : legs) {
                if (leg == null) continue;
                out.pieces.add(leg);
                if (!all.isEmpty() && out.pending.isEmpty()) {
                    RunRoute.Point a = all.get(all.size() - 1), b = leg.get(0);
                    if (RunRoute.distance(a, b) > 3) {
                        out.pending.add(Arrays.asList(a, b)); out.problem = "路段衔接有间隙，请移动衔接点或将相邻一段设为手绘";
                    }
                }
                all.addAll(leg);
            }
            if (!out.pending.isEmpty()) {
                if (out.problem.isEmpty()) out.problem = "还有 " + out.pending.size() + " 段待规划，可重试或选中终点设为手绘";
                return out;
            }
            if (out.maxSnap > 200) { out.problem = "选点距步行路网超过 200 米，请移动选点或使用手绘段"; return out; }
            if (d.closed) {
                if (RunRoute.distance(all.get(0), all.get(all.size() - 1)) > 3) {
                    out.problem = "闭合处未衔接，请移动起点或设置闭合段为手绘"; return out;
                }
                all.add(all.get(0));
            }
            out.route = new RunRoute(d.name, all);
        } catch (Exception e) { out.problem = e.getMessage() == null ? "路线生成失败" : e.getMessage(); }
        return out;
    }
}
