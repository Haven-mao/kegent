package com.haven.kegent.update;

import org.json.JSONObject;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;

/** Release data is untrusted until its values and the downloaded APK have both been checked. */
public final class UpdateManifest {
    public static final long MAX_APK_BYTES = 250L * 1024 * 1024;
    public final String applicationId, channel, versionName, apkUrl, sha256, releaseNotes, publishedAt;
    public final long versionCode, sizeBytes;
    public final int minSdk;
    public UpdateManifest(JSONObject json) throws Exception {
        if (integer(json, "schemaVersion") != 1) throw new IOException("不支持的更新清单格式");
        applicationId = json.getString("applicationId"); channel = json.getString("channel");
        versionName = json.getString("versionName"); apkUrl = https(json.getString("apkUrl"));
        sha256 = json.getString("sha256").toLowerCase(Locale.ROOT);
        versionCode = integer(json, "versionCode"); sizeBytes = integer(json, "sizeBytes");
        long sdk = integer(json, "minSdk"); if (sdk < 1 || sdk > 1000) throw new IOException("更新清单的系统版本无效"); minSdk = (int) sdk;
        releaseNotes = json.optString("releaseNotes", ""); publishedAt = json.optString("publishedAt", "");
        if (!applicationId.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")
                || !"stable".equals(channel) || versionName.trim().isEmpty() || versionName.length() > 80
                || versionCode < 1 || versionCode > Integer.MAX_VALUE || sizeBytes < 1 || sizeBytes > MAX_APK_BYTES
                || minSdk < 1 || minSdk > 1000 || !sha256.matches("[a-f0-9]{64}") || releaseNotes.length() > 16000)
            throw new IOException("更新清单内容无效，请稍后重试");
    }
    private static long integer(JSONObject json, String key) throws Exception {
        Object value = json.get(key);
        if (!(value instanceof Number) || ((Number) value).doubleValue() != ((Number) value).longValue())
            throw new IOException("更新清单中的版本或大小无效");
        return ((Number) value).longValue();
    }
    public void requirePackage(String installedPackage) throws IOException {
        if (!applicationId.equals(installedPackage)) throw new IOException("更新包属于其他应用，已停止更新");
    }
    public static String https(String address) throws Exception {
        URL url = new URL(address);
        if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().isEmpty() || url.getUserInfo() != null
                || url.getRef() != null || address.length() > 4096)
            throw new IOException("更新地址必须是有效的 HTTPS 地址");
        return url.toExternalForm();
    }
    public JSONObject json() throws Exception {
        return new JSONObject().put("schemaVersion", 1).put("applicationId", applicationId).put("channel", channel)
                .put("versionCode", versionCode).put("versionName", versionName).put("minSdk", minSdk)
                .put("apkUrl", apkUrl).put("sizeBytes", sizeBytes).put("sha256", sha256)
                .put("releaseNotes", releaseNotes).put("publishedAt", publishedAt);
    }
}
