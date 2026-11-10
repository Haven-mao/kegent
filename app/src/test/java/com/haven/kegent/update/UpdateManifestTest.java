package com.haven.kegent.update;

import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class UpdateManifestTest {
    static JSONObject manifest() throws Exception {
        return new JSONObject().put("schemaVersion", 1).put("applicationId", "com.haven.kegent").put("channel", "stable")
                .put("versionCode", 12).put("versionName", "1.4.5").put("minSdk", 26).put("apkUrl", "https://updates.example/kegent.apk")
                .put("sizeBytes", 123).put("sha256", new String(new char[64]).replace('\0', 'a')).put("releaseNotes", "改进路线编辑");
    }
    @Test public void manifestRoundTripsWithIntegerVersionAndNotes() throws Exception {
        UpdateManifest m = new UpdateManifest(manifest()); assertEquals(12, m.versionCode); assertEquals("改进路线编辑", new UpdateManifest(m.json()).releaseNotes);
        m.requirePackage("com.haven.kegent");
    }
    @Test(expected = IOException.class) public void anotherApplicationIsRejected() throws Exception { new UpdateManifest(manifest()).requirePackage("other.app"); }
    @Test(expected = IOException.class) public void cleartextDownloadIsRejected() throws Exception { new UpdateManifest(manifest().put("apkUrl", "http://example.org/update.apk")); }
    @Test(expected = IOException.class) public void credentialsInDownloadUrlAreRejected() throws Exception { new UpdateManifest(manifest().put("apkUrl", "https://user:secret@example.org/update.apk")); }
    @Test(expected = IOException.class) public void negativeVersionIsRejected() throws Exception { new UpdateManifest(manifest().put("versionCode", -1)); }
    @Test(expected = IOException.class) public void fractionalVersionsAreRejected() throws Exception { new UpdateManifest(manifest().put("versionCode", 12.5)); }
    @Test(expected = IOException.class) public void oversizedApksAreRejected() throws Exception { new UpdateManifest(manifest().put("sizeBytes", UpdateManifest.MAX_APK_BYTES + 1)); }
    @Test(expected = IOException.class) public void malformedDigestIsRejected() throws Exception { new UpdateManifest(manifest().put("sha256", "1234")); }
    @Test(expected = IOException.class) public void previewChannelCannotReplaceStable() throws Exception { new UpdateManifest(manifest().put("channel", "beta")); }
    @Test(expected = IOException.class) public void sdkCannotOverflowToAnApparentlySupportedVersion() throws Exception { new UpdateManifest(manifest().put("minSdk", 4294967322L)); }
}
