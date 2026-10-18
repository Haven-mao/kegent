package com.haven.kgent.keep;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.haven.kgent.R;
import com.haven.kgent.core.HistoricalRun;
import com.haven.kgent.core.RunRoute;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;

/** Opt-in local contract test. Requires host-owned IPv4 and IPv6 isolation and cleanup. */
@RunWith(AndroidJUnit4.class)
public class KeepHandoffIntegrationTest {
    public static final long START = 1760000000000L, END = START + 300000L;

    @Test
    public void localKeepHandoff() throws Exception {
        Assume.assumeTrue(
                "true"
                        .equals(
                                InstrumentationRegistry.getArguments()
                                        .getString("kgentKeepFixture")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String uid = RootShell.text("stat -c '%u' /data/user/0/com.gotokeep.keep");
        assertTrue(uid.matches("[0-9]+"));
        String rule =
                " -C OUTPUT -m owner --uid-owner "
                        + uid
                        + " -m comment --comment kgent-local-contract-test -j REJECT";
        RootShell.text("iptables" + rule);
        RootShell.text("ip6tables" + rule);
        KeepBridge bridge = new KeepBridge(context);
        KeepBridge.Session session = bridge.inspect();
        String id = KeepRecordBuilder.logId(session.userId, END);
        try {
            RunRoute route;
            try (InputStream in = context.getResources().openRawResource(R.raw.dutpj_mutipoint)) {
                route = RunRoute.readKml(in, "local contract fixture");
            }
            HistoricalRun run =
                    new HistoricalRun(
                            route,
                            START,
                            END,
                            300,
                            170,
                            65,
                            "Asia/Shanghai",
                            System.currentTimeMillis());
            assertEquals(id, bridge.queue(run, session));
            String count =
                    RootShell.text(
                            "sqlite3"
                                + " /data/user/0/com.gotokeep.keep/databases/summary_log_database.db"
                                + " "
                                    + RootShell.quote(
                                            "SELECT count(*) FROM t_log_summary WHERE logId="
                                                    + KeepRecordBuilder.quote(id)
                                                    + ";"));
            assertEquals("1", count);
            long since =
                    Long.parseLong(
                            RootShell.text(
                                    "sqlite3"
                                        + " /data/user/0/com.gotokeep.keep/databases/logger_database.db"
                                        + " 'SELECT max(id) FROM event;'"));
            bridge.openSyncPage();
            android.app.UiAutomation automation =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation();
            boolean displayed = false;
            for (int i = 0; i < 20; i++) {
                Thread.sleep(1000);
                android.view.accessibility.AccessibilityNodeInfo root =
                        automation.getRootInActiveWindow();
                android.view.accessibility.AccessibilityNodeInfo item = find(root, "户外跑步", true);
                if (item != null) {
                    displayed = true;
                    click(item);
                    break;
                }
            }
            assertTrue(
                    "Keep must display the generated record in its own pending-upload list",
                    displayed);
            Thread.sleep(1000);
            android.view.accessibility.AccessibilityNodeInfo window =
                    automation.getRootInActiveWindow();
            dump(window);
            android.graphics.Bitmap shot = automation.takeScreenshot();
            if (shot != null) {
                try (java.io.FileOutputStream file =
                        new java.io.FileOutputStream(
                                new java.io.File(
                                        context.getCacheDir(), "keep-offline-contract.png"))) {
                    shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file);
                }
                shot.recycle();
            }
            android.view.accessibility.AccessibilityNodeInfo upload = find(window, "上传", false);
            if (upload != null) click(upload);
            boolean parsed = false;
            for (int i = 0; i < 15; i++) {
                Thread.sleep(1000);
                String sql =
                        "SELECT count(*) FROM event WHERE id>"
                                + since
                                + " AND tag='TAG_LOG_PARSE' AND message LIKE 'recordType: run%';";
                String found =
                        RootShell.text(
                                "sqlite3"
                                    + " /data/user/0/com.gotokeep.keep/databases/logger_database.db"
                                    + " "
                                        + RootShell.quote(sql));
                if (Integer.parseInt(found) > 0) {
                    parsed = true;
                    break;
                }
            }
            assertTrue(
                    "Keep must read the detailed record through its own parser while"
                        + " network-isolated",
                    parsed);
            String parseFailures =
                    "SELECT count(*) FROM event WHERE id>"
                            + since
                            + " AND tag='TAG_LOG_PARSE' AND message LIKE 'parse failed,%';";
            assertEquals(
                    "0",
                    RootShell.text(
                            "sqlite3 /data/user/0/com.gotokeep.keep/databases/logger_database.db "
                                    + RootShell.quote(parseFailures)));
        } finally {
            RootShell.text("am force-stop com.gotokeep.keep");
            RootShell.text(
                    "sqlite3 /data/user/0/com.gotokeep.keep/databases/summary_log_database.db "
                            + RootShell.quote(
                                    "DELETE FROM t_log_summary WHERE logId="
                                            + KeepRecordBuilder.quote(id)
                                            + ";")
                            + "; rm -f "
                            + RootShell.quote(
                                    "/data/user/0/com.gotokeep.keep/files/local_logs/local_log_"
                                            + id
                                            + "_v2"));
            context.getSharedPreferences("keep_receipts", 0).edit().remove(id).commit();
        }
    }

    private static android.view.accessibility.AccessibilityNodeInfo find(
            android.view.accessibility.AccessibilityNodeInfo node, String text, boolean contains) {
        if (node == null) return null;
        String value = node.getText() == null ? "" : node.getText().toString().trim();
        if (contains ? value.contains(text) : value.equals(text)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo child =
                    find(node.getChild(i), text, contains);
            if (child != null) return child;
        }
        return null;
    }

    private static void click(android.view.accessibility.AccessibilityNodeInfo node) {
        while (node != null && !node.isClickable()) node = node.getParent();
        assertNotNull("A clickable control is required", node);
        assertTrue(
                node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
    }

    private static void dump(android.view.accessibility.AccessibilityNodeInfo node) {
        if (node == null) return;
        String text = node.getText() == null ? "" : node.getText().toString();
        if (text.contains("上传") || text.contains("户外") || text.contains("全选"))
            System.out.println("Keep UI: " + text);
        for (int i = 0; i < node.getChildCount(); i++) dump(node.getChild(i));
    }
}
