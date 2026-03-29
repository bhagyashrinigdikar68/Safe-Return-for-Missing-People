package com.safereturn.notification.model;

import java.util.ArrayList;
import java.util.List;

/** Returned to Flask after attempting all notifications. */
public class NotificationResult {

    private boolean success;
    private String message;
    private List<String> channelResults = new ArrayList<>();

    public static NotificationResult ok(String message) {
        NotificationResult r = new NotificationResult();
        r.success = true;
        r.message = message;
        return r;
    }

    public static NotificationResult error(String message) {
        NotificationResult r = new NotificationResult();
        r.success = false;
        r.message = message;
        return r;
    }

    public void addChannelResult(String result) {
        channelResults.add(result);
    }

    // Getters / Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getChannelResults() { return channelResults; }
    public void setChannelResults(List<String> channelResults) { this.channelResults = channelResults; }
}
