package com.vulndroid.app.labs.deeplinks;

public final class DeepLinkResult {
    public boolean handled;
    public boolean approvedAction;
    public String title = "";
    public String message = "";
    public String exposedData = "";
    public String checks = "";

    public static DeepLinkResult rejected(String title, String message, String checks) {
        DeepLinkResult result = new DeepLinkResult();
        result.handled = true;
        result.approvedAction = false;
        result.title = title;
        result.message = message;
        result.checks = checks;
        return result;
    }
}
