package com.haven.kegent.update;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Bounded HTTPS requests, explicit redirects and resumable APK downloads. */
public final class UpdateTransfer {
    public static final String DEFAULT_SOURCE = "https://api.github.com/repos/Haven-mao/kegent/releases/latest";
    public static final String RELEASE_PAGE = "https://github.com/Haven-mao/kegent/releases";
    public interface Connections { HttpURLConnection open(URL url) throws IOException; }
    public interface Progress { void changed(long bytes, long total); }
    public static final class NoRelease extends IOException { public NoRelease(String message) { super(message); } }
    private final Connections connections;
    private volatile boolean cancelled;
    private volatile HttpURLConnection active;
    public UpdateTransfer() { this(url -> (HttpURLConnection) url.openConnection()); }
    public UpdateTransfer(Connections connections) { this.connections = connections; }
    public void cancel() { cancelled = true; HttpURLConnection c = active; if (c != null) c.disconnect(); }
    private void check() { if (cancelled || Thread.currentThread().isInterrupted()) throw new CancellationException("已暂停，可稍后继续"); }
    private HttpURLConnection open(String address, long offset) throws Exception {
        Set<String> seen = new HashSet<>(); String target = UpdateManifest.https(address);
        for (int i = 0; i < 6; i++) {
            check(); if (!seen.add(target)) throw new IOException("更新服务器重定向异常");
            HttpURLConnection request = connections.open(new URL(target)); active = request;
            request.setInstanceFollowRedirects(false); request.setConnectTimeout(10000); request.setReadTimeout(15000);
            request.setRequestProperty("User-Agent", "kegent/1.4.4 (Android app updater)");
            request.setRequestProperty("Accept", "application/json,application/octet-stream;q=0.9,*/*;q=0.1");
            request.setRequestProperty("Accept-Encoding", "identity");
            if (offset > 0) request.setRequestProperty("Range", "bytes=" + offset + "-");
            int status = request.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = request.getHeaderField("Location"); request.disconnect();
                if (location == null) throw new IOException("更新服务器返回了无效跳转");
                target = UpdateManifest.https(new URL(new URL(target), location).toExternalForm()); continue;
            }
            return request;
        }
        throw new IOException("更新服务器跳转次数过多");
    }
    private void close(HttpURLConnection request) { request.disconnect(); if (active == request) active = null; }
    public JSONObject json(String address) throws Exception {
        HttpURLConnection request = open(address, 0);
        try {
            int status = request.getResponseCode();
            if (status == 404) throw new NoRelease("发布页暂未提供应用内更新包");
            if (status == 403 || status == 429) throw new IOException("更新服务暂时限制访问，请稍后重试");
            if (status != 200) throw new IOException("更新检查失败（HTTP " + status + "）");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream input = request.getInputStream()) {
                byte[] chunk = new byte[8192]; int read;
                while ((read = input.read(chunk)) != -1) { check(); if (buffer.size() + read > 128 * 1024) throw new IOException("更新清单过大"); buffer.write(chunk, 0, read); }
            }
            return new JSONObject(buffer.toString("UTF-8"));
        } finally { close(request); }
    }
    public UpdateManifest manifest(String address) throws Exception {
        JSONObject data = json(address);
        if (data.has("assets")) {
            if (data.optBoolean("draft") || data.optBoolean("prerelease")) throw new NoRelease("暂未提供稳定版更新");
            JSONArray assets = data.getJSONArray("assets"); String source = null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if ("update.json".equals(asset.optString("name"))) { source = asset.getString("browser_download_url"); break; }
            }
            if (source == null) throw new NoRelease("发布页暂未提供应用内更新包，可稍后重试");
            data = json(source);
        }
        return new UpdateManifest(data);
    }
    public static File apk(File directory, UpdateManifest manifest) { return new File(directory, "update-" + manifest.sha256 + ".apk"); }
    public File download(File directory, UpdateManifest manifest, Progress progress) throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建更新下载目录");
        File target = apk(directory, manifest), partial = new File(directory, target.getName() + ".part");
        if (target.isFile()) {
            try { verifyDigest(target, manifest); progress.changed(manifest.sizeBytes, manifest.sizeBytes); return target; }
            catch (IOException e) { Files.deleteIfExists(target.toPath()); }
        }
        if (partial.length() > manifest.sizeBytes) Files.deleteIfExists(partial.toPath());
        if (partial.length() != manifest.sizeBytes) {
            long offset = partial.isFile() ? partial.length() : 0;
            if (directory.getUsableSpace() < manifest.sizeBytes - offset + 2 * 1024 * 1024) throw new IOException("可用空间不足，请清理后重试");
            HttpURLConnection request = open(manifest.apkUrl, offset);
            try {
                int status = request.getResponseCode();
                if (status == 416 && offset > 0) {
                    close(request); Files.deleteIfExists(partial.toPath()); return download(directory, manifest, progress);
                }
                if (status != 200 && status != 206) throw new IOException("下载失败（HTTP " + status + "），可稍后重试");
                if (status == 200) offset = 0;
                else {
                    String range = request.getHeaderField("Content-Range");
                    if (range == null || !range.matches("bytes " + offset + "-[0-9]+/" + manifest.sizeBytes)) throw new IOException("服务器续传响应无效，请重试");
                }
                long expected = manifest.sizeBytes - offset, reported = request.getContentLengthLong();
                if (reported >= 0 && reported != expected) throw new IOException("下载文件大小与发布清单不一致");
                long done = offset; progress.changed(done, manifest.sizeBytes);
                try (InputStream input = request.getInputStream(); FileOutputStream output = new FileOutputStream(partial, offset > 0)) {
                    byte[] chunk = new byte[64 * 1024]; int read;
                    while ((read = input.read(chunk)) != -1) {
                        check(); if (done + read > manifest.sizeBytes) throw new IOException("下载文件大于发布清单，已停止");
                        output.write(chunk, 0, read); done += read; progress.changed(done, manifest.sizeBytes);
                    }
                    output.getFD().sync();
                }
                if (done != manifest.sizeBytes) throw new IOException("下载尚未完成，可继续下载");
            } finally { close(request); }
        }
        check();
        try { verifyDigest(partial, manifest); }
        catch (IOException e) { Files.deleteIfExists(partial.toPath()); throw e; }
        Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); return target;
    }
    public void verifyDigest(File file, UpdateManifest manifest) throws Exception {
        check(); if (!file.isFile() || file.length() != manifest.sizeBytes) throw new IOException("安装包不完整，请重新下载");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) { byte[] chunk = new byte[64 * 1024]; int n; while ((n = input.read(chunk)) != -1) { check(); digest.update(chunk, 0, n); } }
        StringBuilder hex = new StringBuilder(); for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b & 255));
        if (!hex.toString().equals(manifest.sha256)) throw new IOException("安装包校验不通过，请重新下载");
    }
}
