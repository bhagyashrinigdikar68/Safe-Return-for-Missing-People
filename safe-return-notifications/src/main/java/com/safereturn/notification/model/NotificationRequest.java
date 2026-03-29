package com.safereturn.notification.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload sent by Flask api.py → Spring Boot /api/notify after a /recognize call.
 *
 * ── WHAT WAS ADDED ──────────────────────────────────────────────────────────
 *   family_email        → email of the person who FILED the missing-person report
 *   family_phone        → their phone number (for WhatsApp)
 *   reporter_name       → their display name (used in email greeting)
 *   missing_person_name → name of the MISSING person from the report
 *
 * These four fields make notifications user-specific.
 * Before this fix, every match email went to the hardcoded global config list.
 * ────────────────────────────────────────────────────────────────────────────
 */
public class NotificationRequest {

    // ── Face recognition result fields (original, unchanged) ────────────────

    @JsonProperty("person_id")
    private String personId;

    @JsonProperty("person_name")
    private String personName;

    private double confidence;
    private double distance;
    private double threshold;
    private boolean match;
    private String location;
    private String timestamp;

    // ── USER-SPECIFIC recipient fields (NEW) ─────────────────────────────────

    /**
     * Email of the family member who filed the missing-person report.
     * When present → notification sent HERE instead of the global fallback list.
     */
    @JsonProperty("family_email")
    private String familyEmail;

    /**
     * Phone of the reporting family member. e.g. "+919876543210"
     * When present → WhatsApp sent HERE instead of the global config phone.
     */
    @JsonProperty("family_phone")
    private String familyPhone;

    /**
     * Display name of the family contact. Used in email greeting: "Dear Ramesh,"
     */
    @JsonProperty("reporter_name")
    private String reporterName;

    /**
     * Name of the MISSING person from the report filed by the family.
     * Different from personName which is the matched shelter inmate's registered name.
     */
    @JsonProperty("missing_person_name")
    private String missingPersonName;

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** True if this request has a specific family email to notify. */
    public boolean hasSpecificRecipient() {
        return familyEmail != null && !familyEmail.isBlank();
    }

    /** True if this request has a specific family phone to message. */
    public boolean hasSpecificPhone() {
        return familyPhone != null && !familyPhone.isBlank();
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String  getPersonId()                          { return personId; }
    public void    setPersonId(String v)                  { this.personId = v; }

    public String  getPersonName()                        { return personName; }
    public void    setPersonName(String v)                { this.personName = v; }

    public double  getConfidence()                        { return confidence; }
    public void    setConfidence(double v)                { this.confidence = v; }

    public double  getDistance()                          { return distance; }
    public void    setDistance(double v)                  { this.distance = v; }

    public double  getThreshold()                         { return threshold; }
    public void    setThreshold(double v)                 { this.threshold = v; }

    public boolean isMatch()                              { return match; }
    public void    setMatch(boolean v)                    { this.match = v; }

    public String  getLocation()                          { return location; }
    public void    setLocation(String v)                  { this.location = v; }

    public String  getTimestamp()                         { return timestamp; }
    public void    setTimestamp(String v)                 { this.timestamp = v; }

    public String  getFamilyEmail()                       { return familyEmail; }
    public void    setFamilyEmail(String v)               { this.familyEmail = v; }

    public String  getFamilyPhone()                       { return familyPhone; }
    public void    setFamilyPhone(String v)               { this.familyPhone = v; }

    public String  getReporterName()                      { return reporterName; }
    public void    setReporterName(String v)              { this.reporterName = v; }

    public String  getMissingPersonName()                 { return missingPersonName; }
    public void    setMissingPersonName(String v)         { this.missingPersonName = v; }

    @Override
    public String toString() {
        return "NotificationRequest{personId='" + personId + "', confidence=" + confidence
               + ", familyEmail='" + familyEmail + "', match=" + match + "}";
    }
}