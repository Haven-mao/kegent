package com.haven.kegent.route;

import com.haven.kegent.core.RunRoute;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

public class RouteLibraryTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private RunRoute line(String name, double length) { return new RunRoute(name, Arrays.asList(new RunRoute.Point(40, 122), new RouteGeometry.Plane(new RunRoute.Point(40, 122)).point(length, 0))); }
    private InputStream fallback() { return new ByteArrayInputStream(RouteRepository.kml(line("default", 400)).getBytes(StandardCharsets.UTF_8)); }
    @Test public void distinctImportsRemainAvailableAfterSwitchingAndRestart() throws Exception {
        RouteRepository repo = new RouteRepository(temp.getRoot()); String a = repo.saveAndSelect(line("公园", 150), null), b = repo.saveAndSelect(line("河边", 250), null);
        assertNotEquals(a, b); repo = new RouteRepository(temp.getRoot()); assertEquals(2, repo.listSaved().size());
        assertEquals("公园", repo.selectSaved(a).name); assertEquals(150, repo.load(fallback(), "old").lengthMeters, .1);
        assertEquals("河边", repo.selectSaved(b).name); assertEquals(2, repo.listSaved().size());
    }
    @Test public void renamedSelectedRouteUpdatesItsLibraryEntryAndCurrentSelection() throws Exception {
        RouteRepository repo = new RouteRepository(temp.getRoot()); RouteDraft draft = new RouteDraft(); draft.name = "原名";
        String id = repo.saveAndSelect(line(draft.name, 100), draft); repo.renameSaved(id, "校园早跑");
        assertEquals("校园早跑", repo.listSaved().get(0).name); assertEquals("校园早跑", repo.load(fallback(), "old").name);
        assertEquals("校园早跑", repo.selectedDraft().name); assertEquals(id, repo.selectedDraft().savedRouteId);
    }
    @Test public void editingALibraryRouteUpdatesThatEntryWithoutCreatingDuplicates() throws Exception {
        RouteRepository repo = new RouteRepository(temp.getRoot()); RouteDraft draft = new RouteDraft(); String id = repo.saveAndSelect(line("A", 100), draft);
        RouteDraft restored = repo.selectedDraft(); String updatedId = repo.saveAndSelect(line("A2", 200), restored);
        assertEquals(id, updatedId); assertEquals(1, repo.listSaved().size()); assertEquals(200, repo.selectSaved(id).lengthMeters, .1);
    }
    @Test public void removingEntryDoesNotDestroyAnAlreadySelectedSnapshot() throws Exception {
        RouteRepository repo = new RouteRepository(temp.getRoot()); String id = repo.saveAndSelect(line("A", 100), null); repo.deleteSaved(id);
        assertTrue(repo.listSaved().isEmpty()); assertEquals("A", repo.load(fallback(), "old").name);
    }
    @Test public void selectingDefaultDoesNotEraseSavedRoutes() throws Exception {
        RouteRepository repo = new RouteRepository(temp.getRoot()); String id = repo.saveAndSelect(line("A", 100), null);
        repo.select(RunRoute.readKml(fallback(), RouteRepository.DEFAULT_NAME), null);
        assertEquals(RouteRepository.DEFAULT_NAME, repo.load(fallback(), "old").name); assertEquals(1, repo.listSaved().size()); assertEquals("A", repo.selectSaved(id).name);
    }
    @Test public void oldCustomSelectionMigratesOnlyOnce() throws Exception {
        RouteRepository repo = new RouteRepository(temp.getRoot()); repo.select(line("旧路线", 100), null);
        repo.preserveSelection(fallback(), "old"); repo.preserveSelection(fallback(), "old"); assertEquals(1, repo.listSaved().size());
    }
    @Test public void oldDefaultNameMigratesWithoutAddingADuplicateDefaultToLibrary() throws Exception {
        RouteRepository repo = new RouteRepository(temp.getRoot()); repo.select(line("DUTPJ 操场路线", 400), null); repo.preserveSelection(fallback(), "old");
        assertTrue(repo.listSaved().isEmpty()); assertEquals(RouteRepository.DEFAULT_NAME, repo.load(fallback(), "old").name);
    }
    @Test public void damagedSavedFileDoesNotHideOtherSavedRoutes() throws Exception {
        RouteRepository repo = new RouteRepository(temp.getRoot()); repo.saveAndSelect(line("A", 100), null);
        Files.write(new File(temp.getRoot(), "routes/" + UUID.randomUUID() + ".json").toPath(), "broken".getBytes(StandardCharsets.UTF_8)); assertEquals(1, repo.listSaved().size());
    }
    @Test(expected = IOException.class) public void libraryIdsCannotTraverseOutsideTheirDirectory() throws Exception { new RouteRepository(temp.getRoot()).selectSaved("../route-selection"); }
}
