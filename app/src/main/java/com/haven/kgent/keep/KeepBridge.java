package com.haven.kgent.keep;

import android.content.Context;
import android.content.pm.PackageInfo;

import com.haven.kgent.core.HistoricalRun;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Explicit, version-scoped local handoff; Keep itself owns authentication and upload. */
public final class KeepBridge {
    public static final String PACKAGE = "com.gotokeep.keep";
    private static final String DATA = "/data/user/0/" + PACKAGE;
    private static final String DB = DATA + "/databases/summary_log_database.db";
    private final Context context;

    public static final class Session {
        public final String userId, version;

        Session(String userId, String version) {
            this.userId = userId;
            this.version = version;
        }

        public String label() {
            return "Keep "
                    + version
                    + " 已连接 · 账号尾号 "
                    + userId.substring(Math.max(0, userId.length() - 4));
        }
    }

    public KeepBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    public Session inspect() throws Exception {
        if (android.os.Process.myUid() >= 100000) {
            throw new IllegalStateException("当前接入仅支持 Android 主用户中的 Keep");
        }
        PackageInfo info;
        try {
            info = context.getPackageManager().getPackageInfo(PACKAGE, 0);
        } catch (Exception e) {
            throw new IllegalStateException("尚未安装 Keep，请先在本机安装并登录");
        }
        if (!KeepRecordBuilder.KEEP_VERSION.equals(info.versionName) || info.versionCode != 49337)
            throw new IllegalStateException("当前仅适配 Keep 8.7.30（49337），请勿向其他版本写入");
        if (!"0".equals(RootShell.text("id -u")))
            throw new IllegalStateException("此功能需要设备已提供 Root 权限");
        String user =
                MmkvReader.string(
                        RootShell.bytes("cat " + RootShell.quote(DATA + "/files/mmkv/user_info")),
                        "userid");
        if (user.isEmpty()) throw new IllegalStateException("请先在 Keep 中登录账号");
        KeepRecordBuilder.logId(user, 1);
        String schema =
                RootShell.text(
                        "sqlite3 " + RootShell.quote(DB) + " 'PRAGMA table_info(t_log_summary);'");
        for (String column :
                new String[] {
                    "userId",
                    "logId",
                    "workoutName",
                    "totalCalorie",
                    "totalDuration",
                    "startTime",
                    "endTime",
                    "recordType",
                    "sourceType",
                    "outdoorExt",
                    "ktEquipmentExt"
                }) {
            if (!schema.contains("|" + column + "|"))
                throw new IllegalStateException("Keep 本地数据结构不兼容，已停止接入");
        }
        return new Session(user, info.versionName);
    }

    public String queue(HistoricalRun run, Session expected) throws Exception {
        if (expected == null) throw new IllegalStateException("请先检查 Keep 连接");
        Session actual = inspect();
        if (!actual.userId.equals(expected.userId))
            throw new IllegalStateException("Keep 账号已变化，请重新预览后提交");
        if (run.endTime > System.currentTimeMillis()) throw new IllegalStateException("记录结束时间还未到达");
        String id = KeepRecordBuilder.logId(actual.userId, run.endTime);
        if (context.getSharedPreferences("keep_receipts", 0).contains(id))
            throw new IllegalStateException("该时间的记录已交给 Keep，请先到 Keep 核对，避免重复提交");
        String status = RootShell.text("dumpsys activity services " + PACKAGE);
        if (status.contains("OutdoorWorkoutBackgroundService"))
            throw new IllegalStateException("Keep 正在记录运动，请先结束后再补跑");
        String owner = RootShell.text("stat -c '%u:%g' " + RootShell.quote(DATA));
        if (!owner.matches("[0-9]+:[0-9]+")) throw new IllegalStateException("无法确认 Keep 数据目录所属账号");
        File folder = new File(context.getFilesDir(), "keep-handoffs/" + UUID.randomUUID());
        if (!folder.mkdirs()) throw new IllegalStateException("无法创建本地备份目录");
        File json = new File(folder, "record.json"),
                sql = new File(folder, "insert.sql"),
                backup = new File(folder, "summary-before.db");
        write(json, KeepRecordBuilder.build(run, actual.userId).toString());
        write(backup, "");
        String overlap =
                "SELECT count(*) FROM t_log_summary WHERE userId="
                        + KeepRecordBuilder.quote(actual.userId)
                        + " AND recordType='run' AND startTime<"
                        + run.endTime
                        + " AND endTime>"
                        + run.startTime;
        write(
                sql,
                "PRAGMA busy_timeout=5000;\n"
                        + "BEGIN IMMEDIATE;\n"
                        + "CREATE TEMP TABLE kgent_guard(v INTEGER CHECK(v=0));\n"
                        + "INSERT INTO kgent_guard "
                        + overlap
                        + ";\n"
                        + KeepRecordBuilder.insertSql(run, actual.userId, id)
                        + "\nCOMMIT;\n");
        String dest = DATA + "/files/local_logs/local_log_" + id + "_v2",
                stage = dest + ".kgent-" + System.nanoTime();
        String script =
                "set -eu\n"
                        + "case \"$(dumpsys activity services "
                        + PACKAGE
                        + ")\" in *OutdoorWorkoutBackgroundService*) echo KGENT_ACTIVE >&2; exit"
                        + " 20;; esac\n"
                        + "test ! -e "
                        + RootShell.quote(dest)
                        + " || { echo KGENT_DUPLICATE >&2; exit 21; }\n"
                        + "am force-stop "
                        + PACKAGE
                        + "\n"
                        + "sqlite3 "
                        + RootShell.quote(DB)
                        + " "
                        + RootShell.quote(".backup " + RootShell.quote(backup.getAbsolutePath()))
                        + "\n"
                        + "test -d "
                        + RootShell.quote(DATA + "/files/local_logs")
                        + "\n"
                        + "cp "
                        + RootShell.quote(json.getAbsolutePath())
                        + " "
                        + RootShell.quote(stage)
                        + "\n"
                        + "chown "
                        + owner
                        + " "
                        + RootShell.quote(stage)
                        + "\nchmod 600 "
                        + RootShell.quote(stage)
                        + "\n"
                        + "restorecon "
                        + RootShell.quote(stage)
                        + "\n"
                        + "ln "
                        + RootShell.quote(stage)
                        + " "
                        + RootShell.quote(dest)
                        + "\nrm "
                        + RootShell.quote(stage)
                        + "\n"
                        + "if ! sqlite3 -bail "
                        + RootShell.quote(DB)
                        + " < "
                        + RootShell.quote(sql.getAbsolutePath())
                        + "; then rm "
                        + RootShell.quote(dest)
                        + "; exit 22; fi\n"
                        + "echo KGENT_QUEUED\n";
        String result = RootShell.text(script);
        if (!result.contains("KGENT_QUEUED"))
            throw new IllegalStateException("提交状态未确认，请检查 Keep 后再重试");
        JSONObject receipt =
                new JSONObject()
                        .put("startTime", run.startTime)
                        .put("endTime", run.endTime)
                        .put("state", "QUEUED")
                        .put("backup", backup.getAbsolutePath())
                        .put("distance", run.distanceMeters);
        context.getSharedPreferences("keep_receipts", 0)
                .edit()
                .putString(id, receipt.toString())
                .commit();
        return id;
    }

    public void openSyncPage() throws Exception {
        RootShell.text(
                "am start -W -n "
                        + PACKAGE
                        + "/com.gotokeep.keep.dc.business.logsync.activity.LogSyncActivity");
    }

    private static void write(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }
}
