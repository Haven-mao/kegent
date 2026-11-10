package com.haven.kegent.update;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;

public final class ApkVerifier {
    private ApkVerifier() { }
    public static long version(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
    private static int flags() { return PackageManager.GET_SIGNATURES | (Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : 0); }
    public static PackageInfo installed(Context context) throws PackageManager.NameNotFoundException { return context.getPackageManager().getPackageInfo(context.getPackageName(), flags()); }
    public static void verify(Context context, File file, UpdateManifest manifest) throws Exception {
        manifest.requirePackage(context.getPackageName());
        PackageInfo current = installed(context), candidate = context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), flags());
        if (candidate == null || candidate.applicationInfo == null) throw new IOException("下载文件不是有效的 Android 安装包");
        if (!context.getPackageName().equals(candidate.packageName)) throw new IOException("安装包包名不匹配，已停止安装");
        if (version(candidate) != manifest.versionCode || version(candidate) <= version(current)) throw new IOException("安装包版本不匹配或不是更新版本");
        if (!manifest.versionName.equals(candidate.versionName) || candidate.applicationInfo.minSdkVersion != manifest.minSdk)
            throw new IOException("安装包信息与发布清单不一致");
        if (candidate.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) throw new IOException("该版本需要更高的 Android 系统");
        if (!certificates(current).equals(certificates(candidate))) throw new IOException("安装包签名与当前应用不同，无法保留数据覆盖安装");
    }
    public static Set<String> certificates(PackageInfo info) throws Exception {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null ? info.signingInfo.getApkContentsSigners() : null;
        if (signatures == null || signatures.length == 0) signatures = info.signatures;
        if (signatures == null || signatures.length == 0) throw new IOException("无法验证安装包签名");
        Set<String> result = new TreeSet<>();
        for (Signature signature : signatures) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()); StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 255)); result.add(hex.toString());
        }
        return result;
    }
}
