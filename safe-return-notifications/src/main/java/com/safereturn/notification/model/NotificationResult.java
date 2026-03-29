package com.safereturn.notification.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Response returned by /api/notify back to Python flask caller.
 *
 * FIX: Added addChannelResult() method that NotificationOrchestrator calls
 * but the original class was missing.
 */
public class NotificationResult {

    private boolean      success;
    private String       message;
    private String       notifiedEmail;   // actual email that was notified
    private String       notifiedPhone;   // actual phone that was notified
    private List<String> channelResults = new ArrayList<>();

    // ── Static factories ─────────────────────────────────────────────────────

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

    // ── Channel accumulator ──────────────────────────────────────────────────

    /** Called by NotificationOrchestrator to record each channel's outcome. */
    public void addChannelResult(String result) {
        this.channelResults.add(result);
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public boolean      isSuccess()                        { return success; }
    public void         setSuccess(boolean v)              { this.success = v; }

    public String       getMessage()                       { return message; }
    public void         setMessage(String v)               { this.message = v; }

    public String       getNotifiedEmail()                 { return notifiedEmail; }
    public void         setNotifiedEmail(String v)         { this.notifiedEmail = v; }

    public String       getNotifiedPhone()                 { return notifiedPhone; }
    public void         setNotifiedPhone(String v)         { this.notifiedPhone = v; }

    public List<String> getChannelResults()                { return channelResults; }
    public void         setChannelResults(List<String> v)  { this.channelResults = v; }
}