package com.haven.kegent.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import org.maplibre.android.maps.*;
import org.maplibre.android.tile.TileOperation;

/** Shared real basemap for the workspace and editor. No fabricated streets or parks. */
public final class RealMapStyle {
    public static final String DEFAULT_STYLE = "https://tiles.openfreemap.org/styles/liberty";
    private final Context context;
    private final MapLibreMap map;
    private final TextView notice;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean disposed, fallback, hasTile;
    private int generation;
    public RealMapStyle(Context context, MapView view, MapLibreMap map, TextView notice, Runnable changed) {
        this.context = context; this.map = map; this.notice = notice; this.changed = changed;
        map.setPrefetchesTiles(false); map.setPrefetchZoomDelta(0);
        view.addOnDidFailLoadingMapListener(error -> main.post(() -> {
            if (disposed) return;
            if (!fallback) osm(true); else show("地图加载失败，轨迹仍保留；请检查网络或重试地图");
        }));
        view.addOnTileActionListener((operation, x, y, z, wrap, zoom, source) -> {
            if (operation == TileOperation.LoadFromNetwork || operation == TileOperation.LoadFromCache)
                main.post(() -> { if (!disposed && !hasTile) { hasTile = true; notice.setVisibility(View.GONE); changed.run(); } });
            else if (operation == TileOperation.Error) main.post(() -> {
                if (!disposed && !hasTile) show("地图瓦片加载失败，请检查网络；当前背景不代表真实街区");
            });
        });
    }
    public void load() {
        if (disposed) return;
        String style = context.getSharedPreferences("route_editor", Context.MODE_PRIVATE).getString("style", DEFAULT_STYLE);
        fallback = false; hasTile = false; int expected = ++generation; show("正在加载真实地图…");
        if ("osm".equals(style)) { osm(false); return; }
        map.setStyle(new Style.Builder().fromUri(style), loaded -> {
            if (disposed || expected != generation) return;
            changed.run();
            if (hasTile) notice.setVisibility(View.GONE); else show("正在加载真实地图瓦片…");
        });
        main.postDelayed(() -> { if (!disposed && expected == generation && !hasTile) osm(true); }, 18000);
    }
    private void osm(boolean switched) {
        fallback = true; hasTile = false; generation++;
        show(switched ? "切换至 OSM 真实街道底图，正在加载…" : "正在加载 OSM 真实街道底图…");
        String json = "{\"version\":8,\"sources\":{\"osm\":{\"type\":\"raster\",\"tiles\":[\"https://tile.openstreetmap.org/{z}/{x}/{y}.png\"],\"tileSize\":256,\"maxzoom\":19,\"attribution\":\"© OpenStreetMap contributors\"}},\"layers\":[{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":\"#E7F1F7\"}},{\"id\":\"osm\",\"type\":\"raster\",\"source\":\"osm\"}]}";
        map.setStyle(new Style.Builder().fromJson(json), style -> { if (!disposed) changed.run(); });
        int expected = generation;
        main.postDelayed(() -> { if (!disposed && expected == generation && !hasTile) show("真实地图暂不可用，请检查网络或更换地图服务；轨迹仍保留"); }, 18000);
    }
    private void show(String text) { notice.setText(text); notice.setVisibility(View.VISIBLE); }
    public void dispose() { disposed = true; main.removeCallbacksAndMessages(null); }
}
