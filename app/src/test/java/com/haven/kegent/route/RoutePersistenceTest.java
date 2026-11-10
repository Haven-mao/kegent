package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.json.JSONObject;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

public class RoutePersistenceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private InputStream fallback() { return new ByteArrayInputStream("<kml><LineString><coordinates>0,0 0.001,0</coordinates></LineString></kml>".getBytes(StandardCharsets.UTF_8)); }
    @Test public void selectedRouteSurvivesRepositoryRecreationWithIdenticalLengthAndDraft() throws Exception {
        RouteDraft d = new RouteDraft(); d.mode = RouteDraft.Mode.TRACK; d.name = "操场 <A&B>"; d.lane = 2; d.clockwise = true; d.startFraction = .37;
        RunRoute route = new RunRoute(d.name, RouteGeometry.track(d)); RouteRepository repo = new RouteRepository(temp.getRoot());
        repo.select(route, d);
        RunRoute loaded = new RouteRepository(temp.getRoot()).load(fallback(), "legacy");
        assertEquals(route.lengthMeters, loaded.lengthMeters, .000001); assertEquals(d.name, loaded.name); assertTrue(loaded.closed);
        assertEquals(.37, repo.selectedDraft().startFraction, .000001); assertEquals(2, repo.selectedDraft().lane);
        try (InputStream in = new FileInputStream(new File(temp.getRoot(), "selected-route.kml"))) {
            assertEquals(route.lengthMeters, RunRoute.readKml(in, d.name).lengthMeters, .000001);
        }
    }
    @Test public void draftAutosaveDoesNotChangeSelectedGeometry() throws Exception {
        RouteRepository repo = new RouteRepository(temp.getRoot()); RouteDraft d = new RouteDraft();
        repo.select(new RunRoute("selected", RouteGeometry.track(d)), d);
        d.radius = 100; repo.saveDraft(new JSONObject().put("draft", d.json()));
        assertEquals(400, repo.load(fallback(), "legacy").lengthMeters, .5);
        assertEquals(100, RouteDraft.fromJson(repo.readDraft().getJSONObject("draft")).radius, 0);
    }
    @Test public void legacyKmlStillLoadsButCommittedSelectionIsAuthoritative() throws Exception {
        RouteRepository repo = new RouteRepository(temp.getRoot());
        Files.write(new File(temp.getRoot(), "selected-route.kml").toPath(), RouteRepository.kml(new RunRoute("legacy", Arrays.asList(new RunRoute.Point(0, 0), new RunRoute.Point(0, .01)))).getBytes(StandardCharsets.UTF_8));
        assertTrue(repo.load(fallback(), "legacy").lengthMeters > 1000);
        RouteDraft d = new RouteDraft(); repo.select(new RunRoute("new", RouteGeometry.track(d)), null);
        Files.write(new File(temp.getRoot(), "selected-route.kml").toPath(), "broken mirror".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, repo.load(fallback(), "legacy").lengthMeters, .5);
    }
    @Test public void allEditableParametersRoundTripWithoutSharingMutableAnchors() throws Exception {
        RouteDraft d = new RouteDraft(); d.mode = RouteDraft.Mode.MANUAL; d.closed = true; d.smooth = true;
        RouteDraft.Anchor a = new RouteDraft.Anchor(new RunRoute.Point(40, 122)); a.manual = true; d.anchors.add(a);
        RouteDraft restored = RouteDraft.fromJson(d.json()); restored.anchors.get(0).point = new RunRoute.Point(41, 123);
        assertEquals(40, d.anchors.get(0).point.latitude, 0); assertTrue(restored.closed); assertTrue(restored.smooth); assertTrue(restored.anchors.get(0).manual);
    }
    @Test(expected = IllegalArgumentException.class) public void wrongCoordinateSystemIsRejected() throws Exception {
        RouteDraft.fromJson(new RouteDraft().json().put("crs", "GCJ02"));
    }
}
