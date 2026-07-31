package com.safereturn.notification.service;

import com.safereturn.notification.config.NotificationConfig;
import com.safereturn.notification.model.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Sends SMS alerts via Twilio REST API (free trial available).
 *
 * ── FREE SETUP (one-time, ~2 minutes) ────────────────────────────────────────
 *  1. Sign up at https://www.twilio.com/try-twilio  (no credit card needed)
 *  2. From the Console dashboard note your:
 *       Account SID  →  set as TWILIO_ACCOUNT_SID env var
 *       Auth Token   →  set as TWILIO_AUTH_TOKEN env var
 *  3. Get a free Twilio phone number (Console → Phone Numbers → Get a number)
 *       e.g. +12065550100  →  set as TWILIO_FROM_NUMBER env var
 *  4. Free trial lets you send SMS to any VERIFIED number.
 *     Verify the family's number at: Console → Verified Caller IDs
 *  5. Set notification.sms.enabled=true in application.yml
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Recipient priority (same pattern as Email/WhatsApp):
 *   1. req.getFamilyPhone()  ← phone from the report filed by this family
 *   2. config fallback phone ← notification.sms.to-phone in application.yml
 */
@Service
public class SmsNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationService.class);

    private static final String TWILIO_API_URL =
            "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final NotificationConfig config;
    private final HttpClient         httpClient;

    public SmsNotificationService(NotificationConfig config) {
        this.config     = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Send SMS to the family who filed this missing-person report.
     *
     * @return "SMS_OK → <phone>" or "SMS_FAILED: <reason>" or "SMS_SKIPPED (...)"
     */
    public String send(NotificationRequest req) {
        NotificationConfig.SmsConfig sms = config.getSms();

        if (!sms.isEnabled()) {
            return "SMS_SKIPPED (disabled – set notification.sms.enabled=true)";
        }

        // ── Validate Twilio credentials ───────────────────────────────────────
        if (isBlank(sms.getAccountSid()) || isBlank(sms.getAuthToken()) || isBlank(sms.getFromNumber())) {
            return "SMS_SKIPPED (Twilio credentials not configured – check TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN / TWILIO_FROM_NUMBER)";
        }

        // ── Resolve target phone ──────────────────────────────────────────────
        String targetPhone;
        if (req.hasSpecificPhone()) {
            targetPhone = req.getFamilyPhone();
            log.info("SMS: using report-specific phone: {}", targetPhone);
        } else if (!isBlank(sms.getToPhone())) {
            targetPhone = sms.getToPhone();
            log.warn("SMS: no family_phone in request – falling back to config phone: {}", targetPhone);
        } else {
            return "SMS_SKIPPED (no target phone – add family_phone to request or set notification.sms.to-phone)";
        }

        // ── Build Twilio form body ─────────────────────────────────────────────
        String messageBody = buildSmsText(req);
        String formData    = "To="    + urlEncode(targetPhone)
                           + "&From=" + urlEncode(sms.getFromNumber())
                           + "&Body=" + urlEncode(messageBody);

        // ── Basic Auth: Base64(accountSid:authToken) ──────────────────────────
        String credentials = sms.getAccountSid() + ":" + sms.getAuthToken();
        String basicAuth   = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));

        String url = String.format(TWILIO_API_URL, sms.getAccountSid());

        try {
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + basicAuth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {          // Twilio returns 201 Created
                log.info("SMS sent to {} via Twilio", targetPhone);
                return "SMS_OK → " + targetPhone;
            } else {
                log.warn("Twilio HTTP {}: {}", response.statusCode(), response.body());
                return "SMS_FAILED: HTTP " + response.statusCode() + " – " + response.body();
            }

        } catch (Exception e) {
            log.error("SMS send failed: {}", e.getMessage());
            return "SMS_FAILED: " + e.getMessage();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildSmsText(NotificationRequest req) {
        String status  = req.isMatch() ? "MATCH FOUND" : "Possible Match";
        String missing = (!isBlank(req.getMissingPersonName()))
                ? req.getMissingPersonName() : "your family member";
        String greeting = (!isBlank(req.getReporterName()))
                ? "Hello " + req.getReporterName() + ", " : "";

        return String.format(
            "[Safe Return] %s%s - %s detected at %s (%.0f%% confidence). " +
            "Matched inmate: %s. Please log in to Safe Return to verify.",
            greeting,
            status,
            missing,
            req.getLocation() != null ? req.getLocation() : "Unknown",
            req.getConfidence(),
            req.getPersonName()
        );
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
