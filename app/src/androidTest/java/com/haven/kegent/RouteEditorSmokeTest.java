package com.haven.kegent;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.action.*;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import com.haven.kegent.core.RunRoute;
import com.haven.kegent.route.RouteRepository;
import com.haven.kegent.route.RouteDraft;
import org.junit.*;
import org.junit.runner.RunWith;
import org.maplibre.android.maps.MapView;
import java.io.*;
import org.hamcrest.Matcher;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Explicitly isolated applicationId: never edits the owner's installed kegent route. */
@RunWith(AndroidJUnit4.class)
public class RouteEditorSmokeTest {
    private Context context;
    @Before public void isolatedPackageOnly() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue("Run using the isolated .maptest applicationId", context.getPackageName().endsWith(".maptest"));
        try (InputStream in = context.getResources().openRawResource(R.raw.dutpj_mutipoint)) {
            new RouteRepository(context.getFilesDir()).select(RunRoute.readKml(in, "DUTPJ 操场路线"), null);
        }
        new File(context.getFilesDir(), "route-editor-draft.json").delete();
        context.getSharedPreferences("route_editor", Context.MODE_PRIVATE).edit().putString("input_mode", "POINT").apply();
    }
    @Test public void freehandDrawingUndoPanAndPersistenceWorkWithLargeMap() throws Exception {
        Intent intent = new Intent(context, FakeGPSActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        awaitText("地图绘制 · 道路 / 操场"); onView(withText("地图绘制 · 道路 / 操场")).perform(click());
        awaitText("地图绘制"); Thread.sleep(1000);
        onView(withId(R.id.editor_map)).check((view, error) -> {
            if (error != null) throw error;
            int height = context.getResources().getDisplayMetrics().heightPixels;
            System.out.println("Map viewport=" + view.getWidth() + "x" + view.getHeight() + ", display height=" + height);
            assertTrue("Map should use at least 80% of display height", view.getHeight() >= height * .8);
        });
        onView(withText("更多")).perform(click()); onView(withText("新建手动路线")).perform(click()); onView(withText("新建")).perform(click());
        Thread.sleep(700); onView(withId(R.id.editor_input_draw)).perform(click());
        onView(withId(R.id.editor_draw_surface)).perform(drawStroke());
        onView(withText("保存并应用")).check(matches(isEnabled()));
        tool("撤销"); onView(withText("保存并应用")).check(matches(not(isEnabled())));
        tool("重做"); onView(withText("保存并应用")).check(matches(isEnabled()));
        Thread.sleep(700);
        RouteRepository repository = new RouteRepository(context.getFilesDir());
        String before = repository.readDraft().getJSONObject("draft").getJSONArray("anchors").toString();
        onView(withId(R.id.editor_input_move)).perform(click());
        onView(withId(R.id.editor_map)).perform(tap(.35f, .45f));
        Thread.sleep(650);
        assertEquals("Moving the map must not add a point", before, repository.readDraft().getJSONObject("draft").getJSONArray("anchors").toString());
        onView(withId(R.id.editor_input_draw)).perform(click());
        onView(withId(R.id.editor_draw_surface)).perform(twoFingerPan());
        Thread.sleep(650);
        assertEquals("Two fingers must not commit an extra stroke", before, repository.readDraft().getJSONObject("draft").getJSONArray("anchors").toString());
        if (!context.getResources().getConfiguration().isLayoutSizeAtLeast(android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE)
                || context.getResources().getConfiguration().screenWidthDp < 800) {
            onView(withText("工具")).perform(click());
        }
        capture(context.getResources().getConfiguration().screenWidthDp >= 800 ? "freehand-wide" : "freehand-phone");
        onView(withText("保存并应用")).perform(click()); onView(withText("保存")).perform(click());
        awaitText("地图绘制 · 道路 / 操场");
        RouteDraft saved = repository.selectedDraft(); assertEquals(RouteDraft.Mode.MANUAL, saved.mode);
        assertTrue(saved.anchors.size() >= 4); assertTrue(saved.anchors.size() < 100);
        try (InputStream fallback = context.getResources().openRawResource(R.raw.dutpj_mutipoint)) {
            assertTrue(repository.load(fallback, "legacy").lengthMeters > 20);
        }
    }
    private static void tool(String text) {
        boolean[] visible = {false};
        onView(withText(text)).check((view, error) -> { visible[0] = error == null && view.isShown(); });
        if (!visible[0]) onView(withText("工具")).perform(click());
        onView(withText(text)).perform(click());
    }
    private static ViewAction drawStroke() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isDisplayed(); }
            @Override public String getDescription() { return "Draw a curved stroke with one finger"; }
            @Override public void perform(UiController ui, View view) {
                int[] origin = new int[2]; view.getLocationOnScreen(origin); long down = SystemClock.uptimeMillis();
                for (int i = 0; i <= 65; i++) {
                    float t = i / 65f;
                    float x = origin[0] + view.getWidth() * (.20f + .46f * t);
                    float y = origin[1] + view.getHeight() * (.44f - .14f * (float) Math.sin(t * Math.PI * 2));
                    int action = i == 0 ? MotionEvent.ACTION_DOWN : i == 65 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
                    MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
                    event.setSource(InputDevice.SOURCE_TOUCHSCREEN); inject(ui, event); ui.loopMainThreadForAtLeast(12);
                }
                ui.loopMainThreadForAtLeast(200);
            }
        };
    }
    private static ViewAction twoFingerPan() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isDisplayed(); }
            @Override public String getDescription() { return "Pan with two fingers without drawing"; }
            @Override public void perform(UiController ui, View view) {
                int[] origin = new int[2]; view.getLocationOnScreen(origin); long down = SystemClock.uptimeMillis();
                MotionEvent.PointerProperties a = new MotionEvent.PointerProperties(); a.id = 0; a.toolType = MotionEvent.TOOL_TYPE_FINGER;
                MotionEvent.PointerProperties b = new MotionEvent.PointerProperties(); b.id = 1; b.toolType = MotionEvent.TOOL_TYPE_FINGER;
                MotionEvent.PointerProperties[] properties = {a, b};
                for (int i = 0; i <= 15; i++) {
                    MotionEvent.PointerCoords p = new MotionEvent.PointerCoords(), q = new MotionEvent.PointerCoords();
                    p.x = origin[0] + view.getWidth() * (.35f + i * .004f); p.y = origin[1] + view.getHeight() * .4f; p.pressure = p.size = 1;
                    q.x = origin[0] + view.getWidth() * (.55f + i * .004f); q.y = origin[1] + view.getHeight() * .4f; q.pressure = q.size = 1;
                    int count = i == 0 || i == 15 ? 1 : 2;
                    int action = i == 0 ? MotionEvent.ACTION_DOWN : i == 1 ? MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                            : i == 14 ? MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT) : i == 15 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
                    MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count, properties,
                            new MotionEvent.PointerCoords[] {p, q}, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
                    inject(ui, event); ui.loopMainThreadForAtLeast(15);
                }
                ui.loopMainThreadForAtLeast(300);
            }
        };
    }
    private static void inject(UiController ui, MotionEvent event) {
        try { assertTrue(ui.injectMotionEvent(event)); }
        catch (androidx.test.espresso.InjectEventSecurityException e) { throw new AssertionError("Cannot inject test gesture", e); }
        finally { event.recycle(); }
    }
    @Test public void trackManualAndSavedGeometryWorkThroughNativeScreens() throws Exception {
        Intent intent = new Intent(context, FakeGPSActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        awaitText("地图绘制 · 道路 / 操场"); Thread.sleep(10000); capture("main");
        onView(withText("地图绘制 · 道路 / 操场")).perform(click());
        awaitText("地图绘制"); Thread.sleep(1500);
        onView(withText("操场")).perform(click());
        onView(withText(containsString("操场单圈"))).check(matches(isDisplayed())); capture("track");
        onView(withText("跑道参数")).perform(click());
        onView(withText("400 米模板")).perform(click()); onView(withText("跑道参数")).perform(click());
        onView(withHint("道次（1–9）")).perform(replaceText("2"), closeSoftKeyboard());
        onView(withText("顺时针")).perform(click()); onView(withText("应用")).perform(click());
        onView(withText(containsString("407."))).check(matches(isDisplayed()));
        onView(withText("保存并应用")).perform(click()); onView(withText("保存")).perform(click());
        awaitText("地图绘制 · 道路 / 操场");
        Thread.sleep(700);
        try (InputStream fallback = context.getResources().openRawResource(R.raw.dutpj_mutipoint)) {
            RunRoute saved = new RouteRepository(context.getFilesDir()).load(fallback, "legacy");
            assertTrue(saved.closed); assertEquals(400 + 2 * Math.PI * 1.22, saved.lengthMeters, .5);
        }
        onView(withText("地图绘制 · 道路 / 操场")).perform(click()); awaitText("地图绘制"); Thread.sleep(700);
        onView(withText("沿道路")).check(matches(isSelected()));
        onView(withText("保存并应用")).check(matches(not(isEnabled())));
        onView(withText("更多")).perform(click()); onView(withText("新建手动路线")).perform(click()); onView(withText("新建")).perform(click());
        Thread.sleep(800);
        onView(isAssignableFrom(MapView.class)).perform(tap(.28f, .35f));
        onView(isAssignableFrom(MapView.class)).perform(tap(.55f, .35f));
        onView(isAssignableFrom(MapView.class)).perform(tap(.55f, .65f));
        Thread.sleep(500);
        onView(withText("平滑曲线")).perform(click());
        onView(withText(containsString("手动路线"))).check(matches(isDisplayed()));
        onView(withText("保存并应用")).check(matches(isEnabled())); capture("manual");
        onView(withText("保存并应用")).perform(click()); onView(withText("保存")).perform(click());
        awaitText("地图绘制 · 道路 / 操场");
        try (InputStream fallback = context.getResources().openRawResource(R.raw.dutpj_mutipoint)) {
            RouteRepository repository = new RouteRepository(context.getFilesDir());
            RunRoute saved = repository.load(fallback, "legacy"); assertTrue(saved.lengthMeters > 5); assertFalse(saved.closed);
            assertEquals(3, repository.selectedDraft().anchors.size()); assertTrue(saved.size() > 3);
        }
    }
    private static GeneralClickAction tap(float x, float y) {
        return new GeneralClickAction(Tap.SINGLE, view -> {
            int[] screen = new int[2]; view.getLocationOnScreen(screen);
            return new float[] {screen[0] + view.getWidth() * x, screen[1] + view.getHeight() * y};
        }, Press.FINGER, InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.BUTTON_PRIMARY);
    }
    private void capture(String name) throws Exception {
        if ("true".equals(InstrumentationRegistry.getArguments().getString("skipScreenshots"))) return;
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File directory = context.getExternalFilesDir("map-editor-smoke");
        if (directory != null && bitmap != null) try (OutputStream out = new FileOutputStream(new File(directory, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }
    private static void awaitText(String text) throws Exception {
        long until = System.currentTimeMillis() + 15000;
        while (true) {
            try { onView(withText(text)).check(matches(allOf(isDisplayed(), isEnabled()))); return; }
            catch (AssertionError | androidx.test.espresso.NoMatchingViewException e) { if (System.currentTimeMillis() >= until) throw e; Thread.sleep(150); }
        }
    }
}
