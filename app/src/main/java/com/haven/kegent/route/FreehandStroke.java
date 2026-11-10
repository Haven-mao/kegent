package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import java.util.ArrayList;
import java.util.List;

/** Reduce a pen gesture to editable control points, preserving its ends and visible bends. */
public final class FreehandStroke {
    private FreehandStroke() { }
    public static List<RunRoute.Point> prepare(List<RunRoute.Point> raw, double tolerance,
                                               int availablePoints, boolean roads) {
        if (raw.size() > 10000) throw new IllegalArgumentException("这一笔过长，请分段绘制");
        if (!Double.isFinite(tolerance) || tolerance <= 0) throw new IllegalArgumentException("绘制精度无效");
        List<RunRoute.Point> clean = new ArrayList<>();
        for (RunRoute.Point point : raw)
            if (clean.isEmpty() || RunRoute.distance(clean.get(clean.size() - 1), point) > .1) clean.add(point);
        if (clean.size() < 2 || new RunRoute("stroke", clean).lengthMeters < 2)
            throw new IllegalArgumentException("请拖动绘制一段轨迹，或切回点选添加坐标");
        double initial = Math.max(.25, tolerance), current = initial;
        List<RunRoute.Point> result = RouteGeometry.simplify(clean, current);
        if (roads) {
            double maximum = Math.max(8, initial * 4);
            while (result.size() > availablePoints && current < maximum) {
                current = Math.min(maximum, current * 1.5);
                result = RouteGeometry.simplify(clean, current);
            }
        }
        if (result.size() > availablePoints)
            throw new IllegalArgumentException(roads ? "道路控制点超过 20 个，请缩短这一笔、撤销部分选点，或改用手动模式"
                    : "轨迹控制点超过 500 个，请分开保存路线或缩小绘制范围");
        return result;
    }
}
