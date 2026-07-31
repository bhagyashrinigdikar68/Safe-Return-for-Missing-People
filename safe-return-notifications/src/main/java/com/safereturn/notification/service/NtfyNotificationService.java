package com.safereturn.notification.service;

import com.safereturn.notification.config.NotificationConfig;
import com.safereturn.notification.model.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends FREE push notifications via ntfy.sh.
 *
 * FIX: This file was MISSING from the project — NotificationOrchestrator
 *      imports and autowires NtfyNotificationService, but the file was never
 *      created, causing a Spring Boot startup failure:
 *        "Parameter 2 of constructor in NotificationOrchestrator required a
 *         bean of type NtfyNotificationService that could not be found."
 *
 * Setup (free, no account needed):
 *   1. Install the "ntfy" app on Android/iOS
 *   2. Subscribe to your unique topic (set in application.yml: notification.ntfy.topic)
 *   3. Done — notifications appear instantly on your phone
 */
@Service
public class NtfyNotificationService {

    private static final Logger log = LoggerFactory.getLogger(NtfyNotificationService.class);

    private final NotificationConfig config;
    private final HttpClient         httpClient;

    public NtfyNotificationService(NotificationConfig config) {
        this.config     = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Send push notification via ntfy.sh.
     * ntfy.sh notifications go to the shared topic — they are NOT user-specific
     * (unlike email/WhatsApp). Anyone subscribed to the topic receives them.
     *
     * @return "NTFY_OK" or "NTFY_FAILED: <reason>"
     */
    public String send(NotificationRequest req) {
        NotificationConfig.NtfyConfig ntfy = config.getNtfy();

        if (!ntfy.isEnabled()) {
            return "NTFY_SKIPPED (disabled)";
        }

        try {
            String title   = buildTitle(req);
            String message = buildMessage(req);
            String priority = req.isMatch() ? "urgent" : "high";

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(ntfy.getTopicUrl()))
                    .POST(HttpRequest.BodyPublishers.ofString(message))
                    .header("Title",    title)
                    .header("Priority", priority)
                    .header("Tags",     req.isMatch() ? "white_check_mark,sos" : "warning")
                    .build();

            HttpResponse<String> response =
                    httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("ntfy push sent to topic: {}", ntfy.getTopic());
                return "NTFY_OK → " + ntfy.getTopicUrl();
            } else {
                log.warn("ntfy HTTP {}: {}", response.statusCode(), response.body());
                return "NTFY_FAILED: HTTP " + response.statusCode();
            }

        } catch (Exception e) {
            log.error("ntfy send failed: {}", e.getMessage());
            return "NTFY_FAILED: " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String buildTitle(NotificationRequest req) {
        String status = req.isMatch() ? "✅ MATCH FOUND" : "⚠️ Possible Match";
        String name   = (req.getMissingPersonName() != null && !req.getMissingPersonName().isBlank())
                ? req.getMissingPersonName() : req.getPersonName();
        return String.format("[Safe Return] %s – %s", status, name);
    }

    private String buildMessage(NotificationRequest req) {
        String missing = (req.getMissingPersonName() != null && !req.getMissingPersonName().isBlank())
                ? req.getMissingPersonName() : "your family member";
        return String.format(
            "Possible match for %s detected at %s\nConfidence: %.1f%%\nMatched inmate: %s (%s)",
            missing,
            req.getLocation() != null ? req.getLocation() : "Unknown",
            req.getConfidence(),
            req.getPersonName(),
            req.getPersonId()
        );
    }
}