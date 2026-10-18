package com.haven.kgent;

import static org.junit.Assert.*;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.haven.kgent.core.HistoricalRun;
import com.haven.kgent.core.RunRoute;
import com.haven.kgent.keep.KeepRecordBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class KeepStorageContractTest {
    @Test
    public void generatedSummaryMatchesObservedSchemaAndRejectsDuplicate() {
        try (SQLiteDatabase db = SQLiteDatabase.create(null)) {
            db.execSQL(
                    "CREATE TABLE t_log_summary(userId TEXT,logId TEXT NOT NULL PRIMARY"
                        + " KEY,workoutName TEXT,totalCalorie REAL NOT NULL,totalDuration INTEGER"
                        + " NOT NULL,startTime INTEGER NOT NULL,endTime INTEGER NOT NULL,recordType"
                        + " TEXT,sourceType TEXT,outdoorExt TEXT,ktEquipmentExt TEXT)");
            HistoricalRun run =
                    new HistoricalRun(
                            new RunRoute(
                                    "fixture",
                                    Arrays.asList(
                                            new RunRoute.Point(30, 120),
                                            new RunRoute.Point(30.001, 120))),
                            1700000000000L,
                            1700001800000L,
                            360,
                            170,
                            65,
                            "Asia/Shanghai",
                            1800000000000L);
            String id = KeepRecordBuilder.logId("fixture-user", run.endTime),
                    sql = KeepRecordBuilder.insertSql(run, "fixture-user", id);
            db.execSQL(sql);
            try (Cursor c =
                    db.rawQuery(
                            "SELECT recordType,totalDuration,endTime FROM t_log_summary WHERE"
                                    + " logId=?",
                            new String[] {id})) {
                assertTrue(c.moveToFirst());
                assertEquals("run", c.getString(0));
                assertEquals(1800, c.getLong(1));
                assertEquals(run.endTime, c.getLong(2));
            }
            try {
                db.execSQL(sql);
                fail("Duplicate must not replace an existing record");
            } catch (android.database.sqlite.SQLiteConstraintException expected) {
            }
        }
    }
}
