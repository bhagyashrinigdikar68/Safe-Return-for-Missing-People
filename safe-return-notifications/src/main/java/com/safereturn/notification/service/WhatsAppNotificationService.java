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
 * Sends FREE WhatsApp messages via CallMeBot API.
 *
 * ─── One-time setup (5 minutes) ────────────────────────────────────────────
 * 1. Save this number in your contacts:  +34 644 52 74 97  → name it "CallMeBot"
 * 2. Open WhatsApp → send this exact message to CallMeBot:
 *      I allow callmebot to send me messages
 * 3. Wait ~2 minutes → you'll receive your API key via WhatsApp
 * 4. Set in application.yml or env vars:
 *      WHATSAPP_PHONE=+919876543210   (family member's number, international format)
 *      WHATSAPP_API_KEY=xxxxxxxx
 *      notification.whatsapp.enabled=true
 *
 * Limit: ~38 messages/day per phone number (more than enough for alerts).
 * ───────────────────────────────────────────────────────────────────────────
 */
@Service
public class WhatsAppNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);
    private static final String CALLMEBOT_URL =
            "https://api.callmebot.com/whatsapp.php?phone=%s&text=%s&apikey=%s";

    private final NotificationConfig config;
    private final HttpClient httpClient;

    public WhatsAppNotificationService(NotificationConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Sends a WhatsApp message to the configured phone number.
     *
     * @return "WHATSAPP_OK" or "WHATSAPP_FAILED: <reason>" or "WHATSAPP_SKIPPED"
     */
    public String send(NotificationRequest req) {
        NotificationConfig.WhatsAppConfig wa = config.getWhatsapp();

        if (!wa.isEnabled()) {
            return "WHATSAPP_SKIPPED (disabled – see setup instructions in WhatsAppNotificationService.java)";
        }

        if (wa.getPhone() == null || wa.getApiKey() == null
                || wa.getPhone().isBlank() || wa.getApiKey().isBlank()) {
            return "WHATSAPP_SKIPPED (phone/apiKey not configured)";
        }

        try {
            String message  = buildMessage(req);
            String encoded  = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String url      = String.format(CALLMEBOT_URL,
                    URLEncoder.encode(wa.getPhone(), StandardCharsets.UTF_8),
                    encoded,
                    wa.getApiKey());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("WhatsApp notification sent to {}", wa.getPhone());
                return "WHATSAPP_OK → " + wa.getPhone();
            } else {
                log.warn("CallMeBot returned HTTP {}: {}", response.statusCode(), response.body());
                return "WHATSAPP_FAILED: HTTP " + response.statusCode() + " – " + response.body();
            }

        } catch (Exception e) {
            log.error("WhatsApp notification failed: {}", e.getMessage());
            return "WHATSAPP_FAILED: " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String buildMessage(NotificationRequest req) {
        String status   = req.isMatch() ? "✅ CONFIRMED MATCH" : "⚠️ POSSIBLE MATCH";
        String location = req.getLocation() != null ? req.getLocation() : "Unknown";

        return String.format(
                """
                🔍 *Safe Return Alert*
                
                %s
                
                *Person:* %s
                *ID:* %s
                *Confidence:* %.1f%%
                *Location:* %s
                
                Please verify and take action.
                """,
                status,
                req.getPersonName(),
                req.getPersonId(),
                req.getConfidence(),
                location
        );
    }
}
