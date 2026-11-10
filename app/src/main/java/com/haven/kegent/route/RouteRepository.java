package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** One atomic selection is authoritative for both the Activity and the foreground service. */
public final class RouteRepository {
    private static final Object LOCK = new Object();
    private final File directory;
    public RouteRepository(File directory) { this.directory = directory; }
    public RunRoute load(InputStream fallback, String legacyName) throws Exception {
        synchronized (LOCK) {
            File selected = new File(directory, "route-selection.json");
            if (selected.isFile()) {
                JSONObject o = new JSONObject(read(selected));
                if (!"WGS84".equals(o.getString("crs"))) throw new IOException("路线坐标系无效");
                return new RunRoute(o.getString("name"), RouteDraft.decodePoints(o.getJSONArray("points")));
            }
            File legacy = new File(directory, "selected-route.kml");
            if (legacy.isFile()) try (InputStream in = new FileInputStream(legacy)) { return RunRoute.readKml(in, legacyName); }
            return RunRoute.readKml(fallback, "DUTPJ 操场路线");
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
