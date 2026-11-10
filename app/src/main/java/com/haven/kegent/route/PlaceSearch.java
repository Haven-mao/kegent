package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/** Explicit user-triggered Photon/Nominatim search, with cache and at most one request/second. */
public final class PlaceSearch {
    public static final String DEFAULT_ENDPOINT = "https://photon.komoot.io/api/";
    private final File cache;
    private volatile HttpURLConnection connection;
    private long lastRequest;
    public PlaceSearch(File cacheDirectory) { cache = new File(cacheDirectory, "place-search"); }
    public static final class Result {
        public final String name, address;
        public final RunRoute.Point point;
        public final double south, north, west, east;
        private Result(String name, String address, RunRoute.Point point, double south, double north, double west, double east) {
            this.name = name; this.address = address; this.point = point; this.south = south; this.north = north; this.west = west; this.east = east;
        }
    }
    public void cancel() { HttpURLConnection active = connection; if (active != null) active.disconnect(); }
    public List<Result> search(String endpoint, String query) throws Exception {
        WalkingRoutePlanner.validateEndpoint(endpoint);
        String text = query.trim(); if (text.length() < 2 || text.length() > 120) throw new IllegalArgumentException("请输入 2–120 字的地点或地址");
        String key = endpoint + "|" + text.toLowerCase(Locale.ROOT);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(); for (byte b : hash) hex.append(String.format(Locale.US, "%02x", b & 255));
        File cached = new File(cache, hex + ".json");
        if (cached.isFile() && cached.length() < 1024 * 1024 && System.currentTimeMillis() - cached.lastModified() < 7L * 24 * 3600 * 1000)
            try { return decode(new String(Files.readAllBytes(cached.toPath()), StandardCharsets.UTF_8)); } catch (Exception ignored) { }
        long pause = 1100 - (System.currentTimeMillis() - lastRequest); if (pause > 0) Thread.sleep(pause);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        String extra = new URL(endpoint).getPath().contains("search") ? "format=jsonv2&addressdetails=0&accept-language=zh-CN,zh,en&" : "";
        String url = endpoint + (endpoint.contains("?") ? "&" : "?") + extra + "limit=6&q=" + URLEncoder.encode(text, "UTF-8");
        HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection(); connection = request; lastRequest = System.currentTimeMillis();
        try {
            request.setConnectTimeout(8000); request.setReadTimeout(12000);
            request.setRequestProperty("User-Agent", "kegent/1.4.2 (+https://github.com/morluto/rea)"); request.setRequestProperty("Accept", "application/json");
            int code = request.getResponseCode();
            if (code == 429) throw new IOException("搜索请求过于频繁，请稍后再试");
            if (code != 200) throw new IOException("地点搜索暂不可用（HTTP " + code + "），可重试或输入坐标跳转");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = request.getInputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) != -1) { if (Thread.currentThread().isInterrupted()) throw new InterruptedException(); if (bytes.size() + n > 1024 * 1024) throw new IOException("搜索响应过大"); bytes.write(buffer, 0, n); }
            }
            String json = bytes.toString("UTF-8"); List<Result> results = decode(json);
            if (cache.isDirectory() || cache.mkdirs()) try { Files.write(cached.toPath(), json.getBytes(StandardCharsets.UTF_8)); } catch (IOException ignored) { }
            return results;
        } finally { request.disconnect(); if (connection == request) connection = null; }
    }
    public static List<Result> decode(String json) throws Exception {
        if (json.trim().startsWith("[")) return parse(new JSONArray(json));
        JSONArray features = new JSONObject(json).getJSONArray("features"), normalized = new JSONArray();
        for (int i = 0; i < Math.min(features.length(), 10); i++) {
            try {
                JSONObject feature = features.getJSONObject(i), p = feature.getJSONObject("properties");
                JSONArray coordinate = feature.getJSONObject("geometry").getJSONArray("coordinates");
                List<String> parts = new ArrayList<>();
                for (String key : new String[] {"name", "street", "district", "city", "state", "country"}) {
                    String value = p.optString(key, ""); if (!value.isEmpty() && !parts.contains(value)) parts.add(value);
                }
                JSONObject result = new JSONObject().put("lat", coordinate.getDouble(1)).put("lon", coordinate.getDouble(0))
                        .put("name", p.optString("name", "")).put("display_name", String.join(" · ", parts));
                JSONArray extent = p.optJSONArray("extent");
                if (extent != null && extent.length() == 4) result.put("boundingbox", new JSONArray().put(extent.getDouble(3)).put(extent.getDouble(1)).put(extent.getDouble(0)).put(extent.getDouble(2)));
                normalized.put(result);
            } catch (JSONException ignored) { }
        }
        return parse(normalized);
    }
    public static List<Result> parse(JSONArray array) throws Exception {
        List<Result> results = new ArrayList<>();
        for (int i = 0; i < Math.min(array.length(), 10); i++) {
            JSONObject object = array.getJSONObject(i);
            try {
                RunRoute.Point point = new RunRoute.Point(object.getDouble("lat"), object.getDouble("lon"));
                if (Math.abs(point.latitude) > 85) continue;
                String address = object.optString("display_name", ""), name = object.optString("name", "");
                if (name.isEmpty()) name = address.isEmpty() ? "搜索结果" : address.split(",", 2)[0];
                JSONArray bounds = object.optJSONArray("boundingbox"); double s = point.latitude, n = s, w = point.longitude, e = w;
                if (bounds != null && bounds.length() == 4) {
                    double bs = bounds.optDouble(0, s), bn = bounds.optDouble(1, n), bw = bounds.optDouble(2, w), be = bounds.optDouble(3, e);
                    if (Double.isFinite(bs) && Double.isFinite(bn) && Double.isFinite(bw) && Double.isFinite(be)
                            && bs <= bn && bw <= be && bs >= -85 && bn <= 85 && bw >= -180 && be <= 180) { s = bs; n = bn; w = bw; e = be; }
                }
                results.add(new Result(name, address, point, s, n, w, e));
            } catch (IllegalArgumentException | JSONException ignored) { }
        }
        return results;
    }
}
