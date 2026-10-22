package com.vulndroid.app.core;

public final class LabInfo {
    public final String id;
    public final int number;
    public final String title;
    public final String shortTitle;
    public final String tagline;
    public final int iconRes;

    public LabInfo(String id, int number, String title, String shortTitle, String tagline, int iconRes) {
        this.id = id;
        this.number = number;
        this.title = title;
        this.shortTitle = shortTitle;
        this.tagline = tagline;
        this.iconRes = iconRes;
    }
}
