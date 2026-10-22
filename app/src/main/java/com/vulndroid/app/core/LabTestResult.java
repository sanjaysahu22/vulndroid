package com.vulndroid.app.core;

import java.util.Locale;

public final class LabTestResult {

    public enum Status {
        NOT_TESTED,
        VULNERABLE,
        FIXED,
        INCONCLUSIVE
    }

    public final Status status;
    public final String headline;
    public final String evidence;
    public final long timestamp;

    public LabTestResult(Status status, String headline, String evidence, long timestamp) {
        this.status = status;
        this.headline = headline == null ? "" : headline;
        this.evidence = evidence == null ? "" : evidence;
        this.timestamp = timestamp;
    }

    public static LabTestResult notTested() {
        return new LabTestResult(Status.NOT_TESTED, "Not tested in this build yet.", "", 0L);
    }

    public static LabTestResult vulnerable(String headline, String evidence) {
        return new LabTestResult(Status.VULNERABLE, headline, evidence, System.currentTimeMillis());
    }

    public static LabTestResult fixed(String headline, String evidence) {
        return new LabTestResult(Status.FIXED, headline, evidence, System.currentTimeMillis());
    }

    public static LabTestResult inconclusive(String headline, String evidence) {
        return new LabTestResult(Status.INCONCLUSIVE, headline, evidence, System.currentTimeMillis());
    }

    public boolean wasTested() {
        return status != Status.NOT_TESTED;
    }

    public String statusLabel() {
        switch (status) {
            case VULNERABLE:
                return "VULNERABLE";
            case FIXED:
                return "FIXED";
            case INCONCLUSIVE:
                return "INCONCLUSIVE";
            case NOT_TESTED:
            default:
                return "NOT TESTED";
        }
    }

    public String formattedTime() {
        if (timestamp == 0L) {
            return "";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new java.util.Date(timestamp));
    }
}
