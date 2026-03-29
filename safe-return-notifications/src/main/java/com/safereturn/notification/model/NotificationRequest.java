package com.safereturn.notification.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload sent by the Flask face-recognition API to trigger notifications.
 *
 * Flask sends this after a successful /recognize call when
 * confidence >= notification.confidence-threshold (e.g. 70%).
 *
 * Example JSON:
 * {
 *   "person_id":    "SR-2024-001",
 *   "person_name":  "Rajesh Kumar",
 *   "confidence":   87.34,
 *   "distance":     0.1266,
 *   "threshold":    0.40,
 *   "match":        true,
 *   "location":     "Main Gate Camera 2",
 *   "timestamp":    "2024-05-15T14:32:00"
 * }
 */
public class NotificationRequest {

    @JsonProperty("person_id")
    private String personId;

    @JsonProperty("person_name")
    private String personName;

    /** Confidence as a percentage (0–100). */
    private double confidence;

    /** Cosine distance returned by DeepFace (lower = more similar). */
    private double distance;

    /** The GA+PSO optimised threshold used. */
    private double threshold;

    /** True = full match, false = possible / borderline match. */
    private boolean match;

    /** Optional: camera or location name for context. */
    private String location;

    /** ISO-8601 timestamp of the sighting (optional). */
    private String timestamp;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getPersonId() { return personId; }
    public void setPersonId(String personId) { this.personId = personId; }

    public String getPersonName() { return personName; }
    public void setPersonName(String personName) { this.personName = personName; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public boolean isMatch() { return match; }
    public void setMatch(boolean match) { this.match = match; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
