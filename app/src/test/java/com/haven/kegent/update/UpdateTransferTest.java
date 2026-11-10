package com.haven.kegent.update;

import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class UpdateTransferTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private final byte[] body = new byte[180000];
    public UpdateTransferTest() { new Random(7).nextBytes(body); }
    private UpdateManifest offer() throws Exception {
        StringBuilder hex = new StringBuilder(); for (byte b : MessageDigest.getInstance("SHA-256").digest(body)) hex.append(String.format(Locale.US, "%02x", b & 255));
        return new UpdateManifest(UpdateManifestTest.manifest().put("sizeBytes", body.length).put("sha256", hex.toString()));
    }
    private static final class Response extends HttpURLConnection {
        final int code; final byte[] bytes; final Map<String, String> headers = new HashMap<>(); boolean disconnected;
        Response(URL url, int code, byte[] bytes) { super(url); this.code = code; this.bytes = bytes; }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
        @Override public int getResponseCode() { return code; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public long getContentLengthLong() { return bytes.length; }
        @Override public String getHeaderField(String name) { return headers.get(name); }
    }
    @Test public void fullDownloadIsCommittedOnlyAfterMatchingDigest() throws Exception {
        UpdateManifest m = offer(); UpdateTransfer transfer = new UpdateTransfer(url -> new Response(url, 200, body));
        File file = transfer.download(temp.getRoot(), m, (done, total) -> assertTrue(done <= total));
        assertArrayEquals(body, Files.readAllBytes(file.toPath())); assertFalse(new File(file.getPath() + ".part").exists());
    }
    @Test public void pausedDownloadRetainsProgressAndResumesWithRange() throws Exception {
        UpdateManifest m = offer(); UpdateTransfer first = new UpdateTransfer(url -> new Response(url, 200, body));
        try { first.download(temp.getRoot(), m, (done, total) -> { if (done >= 65536) first.cancel(); }); fail(); } catch (CancellationException expected) { }
        File part = new File(UpdateTransfer.apk(temp.getRoot(), m).getPath() + ".part"); long offset = part.length(); assertTrue(offset > 0 && offset < body.length);
        final Response[] response = new Response[1];
        UpdateTransfer resumed = new UpdateTransfer(url -> {
            Response r = new Response(url, 206, Arrays.copyOfRange(body, (int) offset, body.length)); r.headers.put("Content-Range", "bytes " + offset + "-" + (body.length - 1) + "/" + body.length); response[0] = r; return r;
        });
        File completed = resumed.download(temp.getRoot(), m, (done, total) -> { }); assertEquals("bytes=" + offset + "-", response[0].getRequestProperty("Range"));
        assertArrayEquals(body, Files.readAllBytes(completed.toPath()));
    }
    @Test public void serverIgnoringRangeRestartsWithoutAppendingCorruptData() throws Exception {
        UpdateManifest m = offer(); Files.write(new File(UpdateTransfer.apk(temp.getRoot(), m).getPath() + ".part").toPath(), Arrays.copyOf(body, 10));
        File file = new UpdateTransfer(url -> new Response(url, 200, body)).download(temp.getRoot(), m, (done, total) -> { }); assertArrayEquals(body, Files.readAllBytes(file.toPath()));
    }
    @Test public void corruptBodyNeverBecomesAnInstallableFile() throws Exception {
        UpdateManifest m = offer(); byte[] corrupt = body.clone(); corrupt[5]++;
        try { new UpdateTransfer(url -> new Response(url, 200, corrupt)).download(temp.getRoot(), m, (a, b) -> { }); fail(); } catch (IOException expected) { }
        assertFalse(UpdateTransfer.apk(temp.getRoot(), m).exists()); assertFalse(new File(UpdateTransfer.apk(temp.getRoot(), m).getPath() + ".part").exists());
    }
    @Test public void truncatedBodyCanBeRetriedButIsNotCommitted() throws Exception {
        UpdateManifest m = offer();
        try { new UpdateTransfer(url -> new Response(url, 200, Arrays.copyOf(body, 10))).download(temp.getRoot(), m, (a, b) -> { }); fail(); } catch (IOException expected) { }
        assertFalse(UpdateTransfer.apk(temp.getRoot(), m).exists());
    }
    @Test public void aRedirectCannotDowngradeToHttp() throws Exception {
        UpdateTransfer transfer = new UpdateTransfer(url -> { Response r = new Response(url, 302, new byte[0]); r.headers.put("Location", "http://updates.example/file.apk"); return r; });
        try { transfer.download(temp.getRoot(), offer(), (a, b) -> { }); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("HTTPS")); }
    }
    @Test public void redirectLoopsAreBounded() throws Exception {
        int[] opened = {0};
        UpdateTransfer transfer = new UpdateTransfer(url -> { opened[0]++; Response r = new Response(url, 302, new byte[0]); r.headers.put("Location", url.toString()); return r; });
        try { transfer.manifest("https://updates.example/update.json"); fail(); } catch (IOException expected) { assertEquals(1, opened[0]); }
    }
    @Test public void aReleaseWithoutManifestIsReportedAsUnpublished() throws Exception {
        UpdateTransfer transfer = new UpdateTransfer(url -> new Response(url, 200, "{\"draft\":false,\"prerelease\":false,\"assets\":[]}".getBytes(StandardCharsets.UTF_8)));
        try { transfer.manifest(UpdateTransfer.DEFAULT_SOURCE); fail(); } catch (UpdateTransfer.NoRelease expected) { }
    }
    @Test public void releaseAssetIsResolvedAndParsed() throws Exception {
        JSONObject m = offer().json(); final List<String> requests = new ArrayList<>();
        UpdateTransfer transfer = new UpdateTransfer(url -> {
            requests.add(url.toString()); String payload = requests.size() == 1 ? "{\"assets\":[{\"name\":\"update.json\",\"browser_download_url\":\"https://updates.example/update.json\"}]}" : m.toString();
            return new Response(url, 200, payload.getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(12, transfer.manifest(UpdateTransfer.DEFAULT_SOURCE).versionCode); assertEquals(2, requests.size());
    }
    @Test public void incorrectContentRangeIsRejected() throws Exception {
        UpdateManifest m = offer(); Files.write(new File(UpdateTransfer.apk(temp.getRoot(), m).getPath() + ".part").toPath(), Arrays.copyOf(body, 10));
        UpdateTransfer transfer = new UpdateTransfer(url -> { Response r = new Response(url, 206, Arrays.copyOfRange(body, 10, body.length)); r.headers.put("Content-Range", "bytes 0-179999/180000"); return r; });
        try { transfer.download(temp.getRoot(), m, (a, b) -> { }); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("续传")); }
    }
}
