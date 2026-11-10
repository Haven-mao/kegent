package com.haven.kegent;

import android.content.*;
import android.app.Activity;
import android.app.Instrumentation;
import android.net.Uri;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.ScrollView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import com.haven.kegent.core.RunRoute;
import com.haven.kegent.route.*;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class RouteLibrarySmokeTest {
    private Context context;
    private RouteRepository repository;
    @Before public void isolatedOnly() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue(context.getPackageName().endsWith(".maptest")); repository = new RouteRepository(context.getFilesDir());
        for (RouteRepository.SavedRoute entry : repository.listSaved()) repository.deleteSaved(entry.id);
        try (InputStream in = context.getResources().openRawResource(R.raw.dutpj_mutipoint)) { repository.select(RunRoute.readKml(in, RouteRepository.DEFAULT_NAME), null); }
        new File(context.getFilesDir(), "route-editor-draft.json").delete();
        context.getSharedPreferences("route_editor", Context.MODE_PRIVATE).edit().putString("input_mode", "POINT").apply();
    }
    private void open() {
        InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context, FakeGPSActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor(withId(R.id.current_route_selector), 10000);
    }
    @Test public void tabletScrollsIndependentlyAndLibraryNamesSurviveDefaultSelection() throws Exception {
        Assume.assumeTrue(context.getResources().getConfiguration().screenWidthDp >= 840);
        RunRoute path = new RunRoute("晨跑公园", Arrays.asList(new RunRoute.Point(40, 122), new RunRoute.Point(40.001, 122)));
        repository.saveAndSelect(path, null); open(); onView(withText("历史补跑")).perform(click());
        final int[] leftY = {0}; onView(withId(R.id.workspace_left_scroll)).check((view, e) -> { if (e != null) throw e; leftY[0] = view.getScrollY(); });
        onView(withId(R.id.workspace_right_scroll)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(ScrollView.class); }
            @Override public String getDescription() { return "Scroll only the right workspace pane"; }
            @Override public void perform(UiController ui, View view) { ((ScrollView) view).smoothScrollTo(0, 1200); ui.loopMainThreadForAtLeast(500); }
        });
        onView(withId(R.id.workspace_right_scroll)).check((view, e) -> { if (e != null) throw e; assertTrue(view.getScrollY() > 0); });
        onView(withId(R.id.workspace_left_scroll)).check((view, e) -> { if (e != null) throw e; assertEquals(leftY[0], view.getScrollY()); });
        onView(withId(R.id.current_route_selector)).perform(click()); waitFor(withText("已有路线"), 5000);
        onView(withText("使用默认路线")).check(matches(isDisplayed())); onView(withText("晨跑公园")).check(matches(isDisplayed())); capture("route-library");
        onView(withText("管理")).perform(click()); onView(withText("重命名")).perform(click());
        onView(withHint("路线名称")).perform(replaceText("公园一号路线"), closeSoftKeyboard()); onView(withText("保存")).perform(click());
        waitFor(withText("公园一号路线"), 5000); onView(withText("使用默认路线")).perform(click());
        waitFor(allOf(withId(R.id.current_route_selector), isEnabled()), 5000);
        SystemClock.sleep(500); waitFor(withText(RouteRepository.DEFAULT_NAME), 5000); assertEquals(1, repository.listSaved().size());
        onView(withId(R.id.current_route_selector)).perform(click()); waitFor(withText("公园一号路线"), 5000); onView(withText("公园一号路线")).perform(click());
        waitFor(withText("公园一号路线"), 5000); capture("tablet-workspace");
    }
    @Test public void editorStartsEmptyAndCanExplicitlyRecoverLastDraft() throws Exception {
        RouteDraft d = new RouteDraft(); d.mode = RouteDraft.Mode.MANUAL; d.anchors.add(new RouteDraft.Anchor(new RunRoute.Point(40, 122))); d.anchors.add(new RouteDraft.Anchor(new RunRoute.Point(40.001, 122)));
        repository.saveDraft(new org.json.JSONObject().put("draft", d.json())); open(); onView(withText("地图绘制 · 道路 / 操场")).perform(click());
        waitFor(withText("地图绘制"), 5000); Thread.sleep(600);
        onView(withText("保存并应用")).check(matches(not(isEnabled())));
        onView(withText("手动")).perform(click()); onView(withText("保存并应用")).check(matches(not(isEnabled())));
        onView(withText("操场")).perform(click()); onView(withText("手动")).perform(click()); onView(withText("保存并应用")).check(matches(not(isEnabled())));
        onView(withText("更多")).perform(click()); onView(withText("恢复上次草稿")).perform(click());
        onView(withText("保存并应用")).check(matches(isEnabled())); capture("empty-modes-restored-draft");
    }
    @Test public void builtInRouteSharesCardLayoutAndTopButtonsShareOneRow() throws Exception {
        RunRoute custom = new RunRoute("自己的路线", Arrays.asList(new RunRoute.Point(40, 122), new RunRoute.Point(40.001, 122)));
        repository.saveAndSelect(custom, null); open(); onView(withId(R.id.current_route_selector)).perform(click());
        waitFor(withId(R.id.route_library_builtin), 5000);
        final int[] defaultBounds = new int[3];
        onView(withId(R.id.route_library_default)).check((view, error) -> {
            if (error != null) throw error; int[] position = new int[2]; view.getLocationOnScreen(position);
            defaultBounds[0] = position[0]; defaultBounds[1] = position[1]; defaultBounds[2] = view.getWidth();
        });
        onView(withId(R.id.route_library_import)).check((view, error) -> {
            if (error != null) throw error; int[] position = new int[2]; view.getLocationOnScreen(position);
            assertEquals(defaultBounds[1], position[1]); assertTrue(position[0] > defaultBounds[0]);
            assertTrue(Math.abs(defaultBounds[2] - view.getWidth()) <= 1);
        });
        onView(withText(RouteRepository.DEFAULT_NAME)).check(matches(isDescendantOfA(withId(R.id.route_library_builtin))));
        onView(allOf(withText("使用"), isDescendantOfA(withId(R.id.route_library_builtin)))).check(matches(isDisplayed()));
        onView(allOf(withText("管理"), isDescendantOfA(withId(R.id.route_library_builtin)))).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
        capture("route-library-builtin");
        onView(withId(R.id.route_library_builtin)).perform(click()); waitFor(withText(RouteRepository.DEFAULT_NAME), 5000);
        assertEquals(1, repository.listSaved().size());
    }
    @Test public void importedKmlCanBeNamedAndUsedFromSavedList() throws Exception {
        File fixture = new File(context.getCacheDir(), "library-import-test.kml");
        RunRoute route = new RunRoute("fixture", Arrays.asList(new RunRoute.Point(40.68, 122.11), new RunRoute.Point(40.681, 122.111)));
        java.nio.file.Files.write(fixture.toPath(), RouteRepository.kml(route).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        open(); onView(withId(R.id.current_route_selector)).perform(click()); waitFor(withText("已有路线"), 5000);
        IntentFilter pickerFilter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT); pickerFilter.addCategory(Intent.CATEGORY_OPENABLE); pickerFilter.addDataType("*/*");
        Instrumentation.ActivityMonitor picker = instrumentation.addMonitor(pickerFilter,
                new Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent().setData(Uri.fromFile(fixture))), true);
        try {
            onView(withId(R.id.route_library_import)).perform(click()); waitFor(withHint("路线名称"), 5000);
            onView(withHint("路线名称")).perform(replaceText("导入的湖边路线"), closeSoftKeyboard()); onView(withText("保存")).perform(click());
            waitFor(allOf(withId(R.id.current_route_selector), isEnabled()), 5000); SystemClock.sleep(400);
            waitFor(withText("导入的湖边路线"), 5000); assertEquals(1, repository.listSaved().size());
            assertEquals(route.lengthMeters, repository.load(context.getResources().openRawResource(R.raw.dutpj_mutipoint), "old").lengthMeters, .01);
            onView(withId(R.id.current_route_selector)).perform(click()); waitFor(withText("导入的湖边路线"), 5000);
        } finally { instrumentation.removeMonitor(picker); fixture.delete(); }
    }
    @Test public void explicitSearchFindsCampusAndMovesMapWithoutCreatingRoute() throws Exception {
        open(); onView(withText("地图绘制 · 道路 / 操场")).perform(click()); waitFor(withText("地图绘制"), 5000); Thread.sleep(600);
        onView(withText("搜索")).perform(click()); onView(withId(R.id.place_search_input)).perform(replaceText("大连理工大学 盘锦"), closeSoftKeyboard());
        onView(allOf(withText("搜索"), withId(android.R.id.button1))).perform(click());
        waitFor(allOf(isAssignableFrom(com.google.android.material.button.MaterialButton.class), withText(containsString("大连理工大学"))), 25000);
        capture("place-search"); onView(allOf(isAssignableFrom(com.google.android.material.button.MaterialButton.class), withText(containsString("大连理工大学")))).perform(click());
        onView(withText("保存并应用")).check(matches(not(isEnabled()))); Thread.sleep(800); capture("search-map");
    }
    static void waitFor(Matcher<View> matcher, long timeout) {
        long end = SystemClock.uptimeMillis() + timeout;
        while (true) try { onView(matcher).check(matches(isDisplayed())); return; }
        catch (AssertionError | androidx.test.espresso.NoMatchingViewException e) { if (SystemClock.uptimeMillis() > end) throw e; SystemClock.sleep(120); }
    }
    private void capture(String name) throws Exception {
        if ("true".equals(InstrumentationRegistry.getArguments().getString("skipScreenshots"))) return;
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File directory = context.getExternalFilesDir("map-editor-smoke");
        if (directory != null && screenshot != null) try (OutputStream out = new FileOutputStream(new File(directory, name + ".png"))) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }
}
