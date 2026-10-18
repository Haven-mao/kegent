package com.haven.kgent.keep;

import com.haven.kgent.core.HistoricalRun;
import com.haven.kgent.core.RunRoute;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Keep 8.7.30 OutdoorActivity storage format, verified against the installed APK. */
public final class KeepRecordBuilder {
    public static final String KEEP_VERSION = "8.7.30";

    private KeepRecordBuilder() {}

    public static String logId(String userId, long endTime) {
        if (!userId.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Keep 账号标识无效");
        return userId + "_" + (Long.MAX_VALUE - endTime) + "_rn";
    }

    public static JSONObject build(HistoricalRun run, String userId) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("userID", userId).put("trainType", "run").put("dataType", "outdoorRunning");
        data.put("startTime", run.startTime).put("endTime", run.endTime).put("serverEndTime", 0);
        data.put("timezone", run.timezone)
                .put("duration", run.durationSeconds)
                .put("distance", run.distanceMeters);
        data.put("averagePace", run.paceSeconds)
                .put("averageSpeed", run.speed() * 3.6)
                .put("maxSpeed", run.maximumSpeed() * 3.6);
        data.put("averageStepFrequency", run.cadence)
                .put("averageStepLength", run.distanceMeters / run.totalSteps());
        data.put("totalSteps", run.totalSteps()).put("calorie", run.calories());
        data.put("isUploaded", false)
                .put("isDeleted", false)
                .put("offline", true)
                .put("auto_record", false);
        data.put("clientVersion", KEEP_VERSION).put("persistenceVersion", 3).put("status", 0);
        data.put("goalType", "free").put("goalValue", 0).put("privacy", true).put("rawDataURL", "");
        data.put("persistenceFileName", "")
                .put("sportMode", "")
                .put("recoveryFixingUiPending", false);
        data.put("trainingSource", "sports_container_outdoorRunning")
                .put("trainingTrace", "sports_container_outdoorRunning");
        data.put("businessInfo", new JSONObject()).put("businessPassThroughInfo", new JSONObject());
        data.put(
                "sensorInfo",
                new JSONObject()
                        .put("activityType", "outdoorRunning")
                        .put("passThrough", new JSONObject()));
        for (String key :
                new String[] {
                    "flags",
                    "stepPoints",
                    "crossKmPoints",
                    "specialDistancePoints",
                    "eventInfos",
                    "fences",
                    "glideLaps",
                    "actionSample",
                    "distanceSample",
                    "calorieSample",
                    "stepLengthAndFrequencyPoints",
                    "pbInfos",
                    "playedAdLocationAudioEgg",
                    "playedLocationAudioEgg"
                }) data.put(key, new JSONArray());
        data.put(
                "heartRate",
                new JSONObject()
                        .put("averageHeartRate", 0)
                        .put("maxHeartRate", 0)
                        .put("heartRates", new JSONArray())
                        .put("wearableDevices", new JSONArray()));
        JSONArray points = new JSONArray();
        int interval = run.sampleIntervalSeconds();
        for (long second = 0; ; second = Math.min(run.durationSeconds, second + interval)) {
            double fraction = second / (double) run.durationSeconds,
                    distance = run.distanceAtSecond(second);
            RunRoute.Point point = KeepCoordinates.fromGps(run.route.at(distance));
            JSONObject p = new JSONObject();
            p.put("latitude", point.latitude)
                    .put("longitude", point.longitude)
                    .put("timestamp", second * 1000)
                    .put("unixTimestamp", run.startTime + second * 1000);
            p.put("accuracyRadius", 5)
                    .put("altitude", 0)
                    .put("bearing", run.route.bearingAt(distance))
                    .put("locationType", 1);
            double pointSpeed = run.speedAtSecond(second);
            p.put("speed", pointSpeed * 3.6)
                    .put("processLabel", 0)
                    .put("flags", new JSONArray())
                    .put("isPause", false);
            p.put("currentPace", Math.round(1000 / pointSpeed))
                    .put("currentTotalDistance", distance)
                    .put("currentTotalDuration", second);
            p.put("currentTotalSteps", Math.round(run.totalSteps() * fraction))
                    .put(
                            "currentTotalCalories",
                            Math.round(run.calories() * distance / run.distanceMeters));
            p.put("heartRate", 0).put("pressure", 0).put("crossKmMark", 0);
            points.put(p);
            if (second == run.durationSeconds) break;
        }
        data.put("geoPoints", points);
        return data;
    }

    public static String insertSql(HistoricalRun run, String userId, String id) {
        String name =
                "户外跑步 "
                        + String.format(java.util.Locale.ROOT, "%.2f", run.distanceMeters / 1000)
                        + " 公里";
        return "INSERT INTO"
                   + " t_log_summary(userId,logId,workoutName,totalCalorie,totalDuration,startTime,endTime,recordType,sourceType,outdoorExt,ktEquipmentExt)"
                   + " VALUES("
                + quote(userId)
                + ","
                + quote(id)
                + ","
                + quote(name)
                + ","
                + run.calories()
                + ","
                + run.durationSeconds
                + ","
                + run.startTime
                + ","
                + run.endTime
                + ",'run','OFFLINE',NULL,NULL);";
    }

    public static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
