package com.vulndroid.app.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists only the outcome of tests the learner actually ran. */
public final class LabStatusStore {

    private static final String PREFS = "lab_results";
    private static final String KEY_STATUS = "status_";
    private static final String KEY_HEADLINE = "headline_";
    private static final String KEY_EVIDENCE = "evidence_";
    private static final String KEY_TIME = "time_";

    private LabStatusStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static LabTestResult get(Context context, String labId) {
        SharedPreferences p = prefs(context);
        String raw = p.getString(KEY_STATUS + labId, null);
        if (raw == null) {
            return LabTestResult.notTested();
        }
        try {
            LabTestResult.Status status = LabTestResult.Status.valueOf(raw);
            return new LabTestResult(status,
                    p.getString(KEY_HEADLINE + labId, ""),
                    p.getString(KEY_EVIDENCE + labId, ""),
                    p.getLong(KEY_TIME + labId, 0L));
        } catch (IllegalArgumentException e) {
            return LabTestResult.notTested();
        }
    }

    public static void save(Context context, String labId, LabTestResult result) {
        prefs(context).edit()
                .putString(KEY_STATUS + labId, result.status.name())
                .putString(KEY_HEADLINE + labId, result.headline)
                .putString(KEY_EVIDENCE + labId, result.evidence)
                .putLong(KEY_TIME + labId, result.timestamp)
                .apply();
    }

    public static void clear(Context context, String labId) {
        prefs(context).edit()
                .remove(KEY_STATUS + labId)
                .remove(KEY_HEADLINE + labId)
                .remove(KEY_EVIDENCE + labId)
                .remove(KEY_TIME + labId)
                .apply();
    }

    public static void clearAll(Context context) {
        prefs(context).edit().clear().apply();
    }
}
