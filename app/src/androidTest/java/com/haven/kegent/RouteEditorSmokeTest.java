package com.haven.kegent;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.action.*;
import com.haven.kegent.core.RunRoute;
import com.haven.kegent.route.RouteRepository;
import org.junit.*;
import org.junit.runner.RunWith;
import org.maplibre.android.maps.MapView;
import java.io.*;
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
        onView(withText("操场")).check(matches(isSelected()));
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
