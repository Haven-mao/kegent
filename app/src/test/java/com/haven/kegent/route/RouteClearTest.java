package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public final class RouteClearTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private RouteDraft manual() {
        RouteDraft d = new RouteDraft(); d.mode = RouteDraft.Mode.MANUAL;
        d.anchors.add(new RouteDraft.Anchor(new RunRoute.Point(40, 122)));
        d.anchors.add(new RouteDraft.Anchor(new RunRoute.Point(40.001, 122))); return d;
    }
    @Test public void manualClearRemovesGeometryButPreservesTheUsersSmoothingChoiceAndCenter() throws Exception {
        RouteDraft d = manual(); d.smooth = true; d.closed = true; d.closingManual = true; RunRoute.Point center = d.center;
        d.clearGeometry(); assertFalse(d.hasGeometry()); assertTrue(d.anchors.isEmpty()); assertFalse(d.closed); assertFalse(d.closingManual);
        assertTrue(d.smooth); assertSame(center, d.center); assertFalse(RouteDraft.fromJson(d.json()).hasGeometry());
    }
    @Test public void clearRoadCannotReuseACachedLineAsVisibleGeometry() {
        RouteDraft d = manual(); d.mode = RouteDraft.Mode.ROAD;
        Map<String, List<RunRoute.Point>> cache = new HashMap<>();
        cache.put(WalkingRoutePlanner.key("https://route.example/route", d.anchors.get(0).point, d.anchors.get(1).point), d.points());
        d.clearGeometry(); RouteAssembly result = RouteAssembly.build(d, cache, "https://route.example/route");
        assertNull(result.route); assertTrue(result.pieces.isEmpty()); assertTrue(result.pending.isEmpty());
    }
    @Test public void clearedStadiumPersistsAsEmptyAndAnUndoSnapshotRestoresIt() throws Exception {
        RouteDraft d = new RouteDraft(); d.mode = RouteDraft.Mode.TRACK; d.trackInitialized = true;
        String undo = d.json().toString(); d.clearGeometry();
        RouteDraft reloaded = RouteDraft.fromJson(d.json()); assertFalse(reloaded.hasGeometry()); assertTrue(reloaded.trackInitialized);
        assertNull(RouteAssembly.build(reloaded, new HashMap<>(), "https://route.example/route").route);
        RouteDraft restored = RouteDraft.fromJson(new org.json.JSONObject(undo));
        assertTrue(restored.hasGeometry()); assertNotNull(RouteAssembly.build(restored, new HashMap<>(), "https://route.example/route").route);
    }
    @Test public void clearDetachesTheDraftSoSavingANewRouteDoesNotOverwriteTheSavedRoute() throws Exception {
        RouteRepository repo = new RouteRepository(temp.getRoot()); RouteDraft d = manual(); d.name = "原路线";
        String originalId = repo.saveAndSelect(new RunRoute(d.name, d.points()), d); d.clearGeometry(); assertEquals("", d.savedRouteId);
        d.name = "新路线"; d.anchors.add(new RouteDraft.Anchor(new RunRoute.Point(40, 122))); d.anchors.add(new RouteDraft.Anchor(new RunRoute.Point(40.002, 122)));
        String newId = repo.saveAndSelect(new RunRoute(d.name, d.points()), d); assertNotEquals(originalId, newId); assertEquals(2, repo.listSaved().size());
        assertEquals("原路线", repo.selectSaved(originalId).name);
    }
}
