package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Configurable Valhalla endpoint. No Keep credentials or account information is sent. */
public final class WalkingRoutePlanner {
    public static final String DEFAULT_ENDPOINT = "https://valhalla1.openstreetmap.de/route";
    private volatile HttpURLConnection connection;
    private long lastRequest;
    public void cancel() { HttpURLConnection active = connection; if (active != null) active.disconnect(); }
    public List<RunRoute.Point> route(String endpoint, RunRoute.Point a, RunRoute.Point b) throws Exception {
        validateEndpoint(endpoint);
        if (RunRoute.distance(a, b) < 1) throw new IOException("两个选点过近，请移动或删除其中一个");
        if (RunRoute.distance(a, b) > 100000) throw new IOException("单个步行路段不能超过 100 公里");
        long delay = 1100 - (System.currentTimeMillis() - lastRequest);
        if (delay > 0) Thread.sleep(delay);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        JSONObject payload = new JSONObject().put("locations", new JSONArray()
                .put(new JSONObject().put("lat", a.latitude).put("lon", a.longitude))
                .put(new JSONObject().put("lat", b.latitude).put("lon", b.longitude)))
                .put("costing", "pedestrian").put("units", "kilometers")
                .put("directions_options", new JSONObject().put("directions_type", "none"));
        String url = endpoint + (endpoint.contains("?") ? "&" : "?") + "json="
                + URLEncoder.encode(payload.toString(), "UTF-8");
        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
        connection = request; lastRequest = System.currentTimeMillis();
        try {
            request.setConnectTimeout(8000); request.setReadTimeout(15000);
            request.setRequestProperty("User-Agent", "kegent/1.4 (Android route editor)");
            request.setRequestProperty("Accept", "application/json");
            int status = request.getResponseCode();
            if (status == 429) throw new IOException("路线服务繁忙或达到频率限制，请稍后重试");
            if (status != 200) throw new IOException("步行规划失败（HTTP " + status + "），可重试或将该段设为手绘");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream input = request.getInputStream()) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (out.size() + read > 4 * 1024 * 1024) throw new IOException("路线响应过大");
                    out.write(buffer, 0, read);
                }
            }
            return parse(new JSONObject(out.toString("UTF-8")));
        } finally { request.disconnect(); if (connection == request) connection = null; }
    }
    public static void validateEndpoint(String endpoint) throws Exception {
        URL url = new URL(endpoint);
        if (!"https".equals(url.getProtocol()) || url.getHost().isEmpty() || url.getUserInfo() != null || url.getRef() != null)
            throw new IllegalArgumentException("请填写 HTTPS 的 Valhalla /route 接口地址");
    }
    public static List<RunRoute.Point> parse(JSONObject response) throws Exception {
        JSONObject trip = response.optJSONObject("trip");
        if (trip == null || trip.optInt("status", 0) != 0)
            throw new IOException("该区域没有可用步行路线，请调整选点或使用手绘段");
        JSONArray legs = trip.getJSONArray("legs");
        List<RunRoute.Point> result = new ArrayList<>();
        for (int i = 0; i < legs.length(); i++) {
            for (RunRoute.Point p : decodePolyline6(legs.getJSONObject(i).getString("shape"))) {
                if (result.isEmpty() || RunRoute.distance(result.get(result.size() - 1), p) > .05) result.add(p);
                if (result.size() > 50000) throw new IOException("规划结果超过 50,000 个点");
            }
        }
        if (result.size() < 2) throw new IOException("路线服务没有返回有效轨迹");
        return result;
    }
    public static List<RunRoute.Point> decodePolyline6(String encoded) throws IOException {
        List<RunRoute.Point> result = new ArrayList<>();
        int[] index = {0}; long lat = 0, lon = 0;
        while (index[0] < encoded.length()) {
            lat += value(encoded, index); lon += value(encoded, index);
            try { result.add(new RunRoute.Point(lat / 1e6, lon / 1e6)); }
            catch (IllegalArgumentException e) { throw new IOException("路线服务返回了无效坐标", e); }
            if (result.size() > 50000) throw new IOException("路线点数过多");
        }
        return result;
    }
    private static long value(String s, int[] index) throws IOException {
        long value = 0; int shift = 0, part;
        do {
            if (index[0] >= s.length() || shift > 30) throw new IOException("轨迹编码不完整");
            part = s.charAt(index[0]++) - 63;
            if (part < 0 || part > 63) throw new IOException("轨迹编码无效");
            value |= (long) (part & 31) << shift; shift += 5;
        } while (part >= 32);
        return (value & 1) != 0 ? ~(value >> 1) : value >> 1;
    }
    public static String key(String endpoint, RunRoute.Point a, RunRoute.Point b) {
        return endpoint + "|" + a.latitude + "," + a.longitude + "|" + b.latitude + "," + b.longitude;
    }
}
