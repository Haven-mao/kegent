package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;

/** One atomic selection is authoritative for both the Activity and the foreground service. */
public final class RouteRepository {
    public static final String DEFAULT_NAME = "大连理工大学盘锦校区路线";
    private static final Object LOCK = new Object();
    private final File directory;
    public RouteRepository(File directory) { this.directory = directory; }
    public RunRoute load(InputStream fallback, String legacyName) throws Exception {
        synchronized (LOCK) {
            File selected = new File(directory, "route-selection.json");
            if (selected.isFile()) {
                JSONObject o = new JSONObject(read(selected));
                if (!"WGS84".equals(o.getString("crs"))) throw new IOException("路线坐标系无效");
                return route(o);
            }
            File legacy = new File(directory, "selected-route.kml");
            if (legacy.isFile()) try (InputStream in = new FileInputStream(legacy)) { return RunRoute.readKml(in, "DUTPJ 操场路线".equals(legacyName) ? DEFAULT_NAME : legacyName); }
            return RunRoute.readKml(fallback, DEFAULT_NAME);
        }
    }
    public void select(RunRoute route, RouteDraft draft) throws Exception {
        synchronized (LOCK) {
            List<RunRoute.Point> points = new ArrayList<>();
            for (int i = 0; i < route.size(); i++) points.add(route.get(i));
            String kml = kml(route);
            if (kml.getBytes(StandardCharsets.UTF_8).length > 4 * 1024 * 1024)
                throw new IllegalArgumentException("路线文件超过 4 MB，请减少采样点");
            JSONObject selection = new JSONObject().put("version", 1).put("crs", "WGS84")
                    .put("name", route.name).put("points", RouteDraft.encodePoints(points));
            if (draft != null) selection.put("draft", draft.json());
            atomic(new File(directory, "route-selection.json"), selection.toString());
            // Compatibility mirror. Failure cannot turn a committed selection into a reported failure.
            try { atomic(new File(directory, "selected-route.kml"), kml); } catch (IOException ignored) { }
        }
    }
    public static RunRoute rename(RunRoute route, String name) {
        String cleaned = name.trim();
        if (cleaned.isEmpty() || cleaned.length() > 80) throw new IllegalArgumentException("路线名称请填写 1–80 个字");
        List<RunRoute.Point> points = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) points.add(route.get(i));
        return new RunRoute(cleaned, points);
    }
    private static RunRoute route(JSONObject data) throws Exception {
        if (!"WGS84".equals(data.getString("crs"))) throw new IOException("路线坐标系无效");
        String name = data.getString("name");
        if ("DUTPJ 操场路线".equals(name)) name = DEFAULT_NAME;
        return new RunRoute(name, RouteDraft.decodePoints(data.getJSONArray("points")));
    }
    public static final class SavedRoute {
        public final String id, name;
        public final double meters;
        public final int points;
        public final boolean closed;
        public final long updated;
        private SavedRoute(String id, RunRoute route, long updated) {
            this.id = id; name = route.name; meters = route.lengthMeters; points = route.size(); closed = route.closed; this.updated = updated;
        }
    }
    private File savedFile(String id) throws IOException {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw new IOException("无效的路线标识");
        return new File(new File(directory, "routes"), id + ".json");
    }
    public List<SavedRoute> listSaved() throws Exception {
        synchronized (LOCK) {
            List<SavedRoute> saved = new ArrayList<>(); File folder = new File(directory, "routes");
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) for (File file : files) {
                String id = file.getName().replace(".json", "");
                // One damaged saved route does not hide the rest of the library.
                try { JSONObject data = new JSONObject(read(savedFile(id))); saved.add(new SavedRoute(id, route(data), data.optLong("updated", file.lastModified()))); }
                catch (Exception ignored) { }
            }
            saved.sort(Comparator.comparingLong((SavedRoute entry) -> entry.updated).reversed()); return saved;
        }
    }
    public String saveAndSelect(RunRoute route, RouteDraft draft) throws Exception {
        synchronized (LOCK) {
            String id = draft == null ? null : draft.savedRouteId;
            if (id == null || id.isEmpty() || !savedFile(id).isFile()) id = UUID.randomUUID().toString();
            File folder = new File(directory, "routes");
            if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("无法建立路线存储目录");
            List<RunRoute.Point> points = new ArrayList<>(); for (int i = 0; i < route.size(); i++) points.add(route.get(i));
            if (kml(route).getBytes(StandardCharsets.UTF_8).length > 4 * 1024 * 1024) throw new IllegalArgumentException("路线文件超过 4 MB");
            JSONObject data = new JSONObject().put("version", 1).put("crs", "WGS84").put("name", route.name)
                    .put("points", RouteDraft.encodePoints(points)).put("updated", System.currentTimeMillis()).put("routeId", id);
            if (draft != null) { draft.savedRouteId = id; data.put("draft", draft.json()); }
            atomic(savedFile(id), data.toString());
            atomic(new File(directory, "route-selection.json"), data.toString());
            try { atomic(new File(directory, "selected-route.kml"), kml(route)); } catch (IOException ignored) { }
            return id;
        }
    }
    public RunRoute selectSaved(String id) throws Exception {
        synchronized (LOCK) {
            JSONObject data = new JSONObject(read(savedFile(id))); RunRoute route = route(data);
            atomic(new File(directory, "route-selection.json"), data.toString());
            try { atomic(new File(directory, "selected-route.kml"), kml(route)); } catch (IOException ignored) { }
            return route;
        }
    }
    public void renameSaved(String id, String name) throws Exception {
        synchronized (LOCK) {
            File file = savedFile(id); JSONObject data = new JSONObject(read(file)); RunRoute renamed = rename(route(data), name);
            data.put("name", renamed.name).put("updated", System.currentTimeMillis());
            if (data.optJSONObject("draft") != null) data.getJSONObject("draft").put("name", renamed.name);
            atomic(file, data.toString());
            File selection = new File(directory, "route-selection.json");
            if (selection.isFile() && id.equals(new JSONObject(read(selection)).optString("routeId"))) selectSaved(id);
        }
    }
    public void deleteSaved(String id) throws Exception {
        synchronized (LOCK) {
            File file = savedFile(id); if (file.exists() && !file.delete()) throw new IOException("路线删除失败");
        }
    }
    /** Preserve an existing custom selection when the library is first introduced. */
    public void preserveSelection(InputStream fallback, String legacyName) throws Exception {
        synchronized (LOCK) {
            File selection = new File(directory, "route-selection.json");
            if (!selection.isFile() && !new File(directory, "selected-route.kml").isFile()) return;
            if (selection.isFile() && !new JSONObject(read(selection)).optString("routeId").isEmpty()) return;
            RunRoute current = load(fallback, legacyName);
            if (!DEFAULT_NAME.equals(current.name)) saveAndSelect(current, selectedDraft());
        }
    }
    public RouteDraft selectedDraft() throws Exception {
        synchronized (LOCK) {
            File file = new File(directory, "route-selection.json");
            if (!file.isFile()) return null;
            JSONObject draft = new JSONObject(read(file)).optJSONObject("draft");
            return draft == null ? null : RouteDraft.fromJson(draft);
        }
    }
    public JSONObject readDraft() throws Exception {
        synchronized (LOCK) {
            File file = new File(directory, "route-editor-draft.json");
            return file.isFile() ? new JSONObject(read(file)) : null;
        }
    }
    public void saveDraft(JSONObject data) throws Exception {
        synchronized (LOCK) { atomic(new File(directory, "route-editor-draft.json"), data.toString()); }
    }
    public static String kml(RunRoute route) {
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><Placemark><name>");
        out.append(route.name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
        out.append("</name><LineString><coordinates>");
        for (int i = 0; i < route.size(); i++) {
            RunRoute.Point p = route.get(i); out.append(p.longitude).append(',').append(p.latitude).append(",0 ");
        }
        return out.append("</coordinates></LineString></Placemark></Document></kml>").toString();
    }
    private static String read(File file) throws IOException {
        if (file.length() > 12 * 1024 * 1024) throw new IOException("路线文件过大");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
    private static void atomic(File target, String data) throws IOException {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(data.getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
        }
        try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
