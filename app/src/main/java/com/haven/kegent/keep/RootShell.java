package com.haven.kegent.keep;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

final class RootShell {
    static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    static String text(String command) throws Exception {
        return new String(bytes(command), StandardCharsets.UTF_8).trim();
    }

    static byte[] bytes(String command) throws Exception {
        Process process;
        try {
            process = new ProcessBuilder("su", "-c", command).start();
        } catch (IOException e) {
            throw new IOException("未检测到 Root，仍可预览和导出记录");
        }
        FutureTask<byte[]> out = new FutureTask<>(() -> read(process.getInputStream()));
        FutureTask<byte[]> err = new FutureTask<>(() -> read(process.getErrorStream()));
        Thread a = new Thread(out, "kegent-root-out"), b = new Thread(err, "kegent-root-err");
        a.setDaemon(true);
        b.setDaemon(true);
        a.start();
        b.start();
        try {
            if (!process.waitFor(20, TimeUnit.SECONDS))
                throw new IOException("Root 操作超时，请检查设备授权后重试");
            byte[] output = out.get(3, TimeUnit.SECONDS);
            String errors = new String(err.get(3, TimeUnit.SECONDS), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                if (errors.contains("KEGENT_ACTIVE"))
                    throw new IOException("Keep 正在记录运动，请先在 Keep 中结束运动");
                if (errors.contains("KEGENT_DUPLICATE"))
                    throw new IOException("该记录已存在，请到 Keep 中查看待同步记录");
                if (errors.contains("CHECK constraint failed"))
                    throw new IOException("此时间段与本地待同步记录重叠");
                throw new IOException(
                        "Keep 接入未完成："
                                + (errors.contains("Permission denied")
                                        ? "Root 授权不足"
                                        : errors.contains("not found")
                                                ? "设备缺少 sqlite3 支持"
                                                : "本地检查或写入失败，原记录未覆盖"));
            }
            return output;
        } finally {
            process.destroy();
        }
    }

    private static byte[] read(InputStream stream) throws IOException {
        try (InputStream input = stream;
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = input.read(buf)) != -1) {
                if (out.size() + n > 8 * 1024 * 1024) throw new IOException("读取内容超过限制");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
