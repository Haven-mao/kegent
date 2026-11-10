package com.haven.kegent;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.View;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.haven.kegent.core.RunRoute;
import com.haven.kegent.route.RouteRepository;
import com.haven.kegent.update.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Only an explicitly built isolated application can enter this test suite. */
@RunWith(AndroidJUnit4.class)
public final class AppUpdateSmokeTest {
    private Context context;
    @Before public void isolatedOnly() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue(context.getPackageName().endsWith(".updatetest"));
    }
    private File fixture(String name) { return new File(context.getExternalFilesDir("fixtures"), name); }
    private UpdateManifest metadata(File file) throws Exception {
        PackageInfo apk = context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0); assertNotNull(apk);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) { byte[] bytes = new byte[65536]; int n; while ((n = in.read(bytes)) != -1) sha.update(bytes, 0, n); }
        StringBuilder hex = new StringBuilder(); for (byte b : sha.digest()) hex.append(String.format(Locale.US, "%02x", b & 255));
        return new UpdateManifest(new JSONObject().put("schemaVersion", 1).put("applicationId", context.getPackageName()).put("channel", "stable")
                .put("versionCode", ApkVerifier.version(apk)).put("versionName", apk.versionName).put("minSdk", apk.applicationInfo.minSdkVersion)
                .put("apkUrl", "https://updates.example/kegent.apk").put("sizeBytes", file.length()).put("sha256", hex.toString()).put("releaseNotes", "隔离测试更新：验证系统安装与路线数据保留。"));
    }
    private UpdateManifest stage() throws Exception {
        File candidate = fixture("candidate.apk"); assertTrue(candidate.isFile()); UpdateManifest offer = metadata(candidate);
        File directory = new File(context.getFilesDir(), "app-updates"); assertTrue(directory.isDirectory() || directory.mkdirs());
        Files.copy(candidate.toPath(), UpdateTransfer.apk(directory, offer).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.write(new File(directory, "offer.json").toPath(), offer.json().toString().getBytes(StandardCharsets.UTF_8));
        return offer;
    }
    private void openUpdater() {
        InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context, AppUpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    private static void waitForInstall() {
        long end = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < end) {
            boolean[] ready = {false}; onView(withId(R.id.update_install)).check((view, error) -> ready[0] = error == null && view.isShown() && view.isEnabled());
            if (ready[0]) return; SystemClock.sleep(100);
        }
        throw new AssertionError("Verified update was not ready within 15 seconds");
    }
    @Test public void candidatePackageAndCertificateChecksProtectTheExistingApplication() throws Exception {
        File candidate = fixture("candidate.apk"); UpdateManifest offer = metadata(candidate);
        new UpdateTransfer().verifyDigest(candidate, offer); ApkVerifier.verify(context, candidate, offer);
        try { ApkVerifier.verify(context, fixture("foreign.apk"), metadata(fixture("foreign.apk"))); fail("Foreign signer accepted"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("签名")); }
        try { ApkVerifier.verify(context, fixture("wrong-package.apk"), metadata(fixture("wrong-package.apk"))); fail("Foreign package accepted"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("包名")); }
        File installed = new File(context.getApplicationInfo().sourceDir);
        try { ApkVerifier.verify(context, installed, metadata(installed)); fail("Installed version accepted as newer"); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("版本")); }
    }
    @Test public void verifiedDownloadRestoresAndInstallerCancellationKeepsCurrentVersion() throws Exception {
        UpdateManifest offer = stage(); long version = ApkVerifier.version(ApkVerifier.installed(context));
        openUpdater(); waitForInstall(); onView(withText("发布版本 " + offer.versionName + " · " + String.format(Locale.CHINA, "%.1f MB", offer.sizeBytes / 1048576.0))).check(matches(isDisplayed()));
        File candidate = UpdateTransfer.apk(new File(context.getFilesDir(), "app-updates"), offer);
        assertEquals("content", FileProvider.getUriForFile(context, context.getPackageName() + ".updates", candidate).getScheme());
        File outside = new File(context.getFilesDir(), "route-selection.json");
        try { FileProvider.getUriForFile(context, context.getPackageName() + ".updates", outside); fail("Provider exposed route data"); } catch (IllegalArgumentException expected) { }
        Assume.assumeTrue(context.getPackageManager().canRequestPackageInstalls());
        IntentFilter filter = new IntentFilter(Intent.ACTION_INSTALL_PACKAGE); filter.addDataType("application/vnd.android.package-archive"); filter.addDataScheme("content");
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor installer = instrumentation.addMonitor(filter, new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
        try {
            onView(withId(R.id.update_install)).perform(click());
            long end = SystemClock.uptimeMillis() + 8000;
            while (installer.getHits() == 0 && SystemClock.uptimeMillis() < end) SystemClock.sleep(100);
            assertTrue(installer.getHits() > 0);
            assertEquals(version, ApkVerifier.version(ApkVerifier.installed(context)));
            assertTrue(candidate.isFile());
        } finally { instrumentation.removeMonitor(installer); }
    }
    @Test public void stageAndOpenTheActualSystemInstaller() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("performRealInstall")));
        UpdateManifest offer = stage();
        context.getSharedPreferences("workspace", 0).edit().putString("update_test_marker", "retain-me").commit();
        new RouteRepository(context.getFilesDir()).saveAndSelect(new RunRoute("更新前保留路线", Arrays.asList(new RunRoute.Point(40, 122), new RunRoute.Point(40.001, 122))), null);
        assertTrue(context.getPackageManager().canRequestPackageInstalls()); assertTrue(offer.versionCode > ApkVerifier.version(ApkVerifier.installed(context)));
        openUpdater(); waitForInstall(); onView(withId(R.id.update_install)).perform(click());
        // The external test driver confirms the real installer; replacement stops this instrumentation.
        SystemClock.sleep(45000);
    }
    @Test public void afterSystemUpgradeTheVersionAndSavedRouteArePreserved() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("verifyRealInstall")));
        assertEquals(12, ApkVerifier.version(ApkVerifier.installed(context)));
        assertEquals("retain-me", context.getSharedPreferences("workspace", 0).getString("update_test_marker", ""));
        List<RouteRepository.SavedRoute> routes = new RouteRepository(context.getFilesDir()).listSaved();
        assertTrue(routes.stream().anyMatch(route -> "更新前保留路线".equals(route.name)));
    }
    @Test public void helpMenuOpensUpdaterAndTheRealFeedHasABoundedResult() throws Exception {
        File offer = new File(context.getFilesDir(), "app-updates/offer.json"); offer.delete();
        context.getSharedPreferences("app_update", 0).edit().remove("source").commit();
        InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context, FakeGPSActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        onView(withText("帮助与更新")).perform(click()); onView(withText("检查更新")).perform(click());
        long end = SystemClock.uptimeMillis() + 35000; boolean[] done = {false};
        while (SystemClock.uptimeMillis() < end) {
            onView(withId(R.id.update_check)).check((view, error) -> done[0] = error == null && view.isEnabled());
            if (done[0]) break; SystemClock.sleep(150);
        }
        assertTrue("Update check must finish or show a recoverable error", done[0]);
        onView(withId(R.id.update_status)).check((view, error) -> {
            if (error != null) throw error; String text = ((android.widget.TextView) view).getText().toString();
            assertFalse(text.isEmpty()); System.out.println("Live update feed status: " + text);
        });
    }
}
