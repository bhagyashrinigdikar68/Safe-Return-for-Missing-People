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
 * ── KEY FIX ──────────────────────────────────────────────────────────────────
 * BEFORE: always sent to config.getWhatsapp().getPhone()
 *         → hardcoded "+919876543210" regardless of who filed the report
 *
 * AFTER:  if request.getFamilyPhone() present → send to THAT phone  [NEW]
 *         if not present                      → fall back to config phone
 *
 * Note: CallMeBot requires each phone number to be individually activated.
 * See setup instructions below if the family phone hasn't been activated yet.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * One-time setup per phone number:
 *   1. Save +34 644 52 74 97 as "CallMeBot" in WhatsApp contacts
 *   2. Send: "I allow callmebot to send me messages"
 *   3. Receive your API key via WhatsApp (~2 min)
 *   4. Set WHATSAPP_API_KEY in application.yml or env
 */
@Service
public class WhatsAppNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);
    private static final String CALLMEBOT_URL =
            "https://api.callmebot.com/whatsapp.php?phone=%s&text=%s&apikey=%s";

    private final NotificationConfig config;
    private final HttpClient         httpClient;

    public WhatsAppNotificationService(NotificationConfig config) {
        this.config     = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Send WhatsApp message to the appropriate phone number.
     *
     * Priority:
     *   1. req.getFamilyPhone() ← specific reporter's phone  [NEW]
     *   2. config phone          ← global fallback
     *
     * @return "WHATSAPP_OK → <phone>" or "WHATSAPP_FAILED: <reason>"
     */
    public String send(NotificationRequest req) {
        NotificationConfig.WhatsAppConfig wa = config.getWhatsapp();

        if (!wa.isEnabled()) {
            return "WHATSAPP_SKIPPED (disabled)";
        }

        // ── Resolve target phone ──────────────────────────────────────────────
        String targetPhone;
        if (req.hasSpecificPhone()) {
            targetPhone = req.getFamilyPhone();
            log.info("Using user-specific WhatsApp phone: {}", targetPhone);
        } else if (wa.getPhone() != null && !wa.getPhone().isBlank()) {
            targetPhone = wa.getPhone();
            log.warn("No family_phone in request – falling back to config phone: {}", targetPhone);
        } else {
            return "WHATSAPP_SKIPPED (no phone configured)";
        }

        if (wa.getApiKey() == null || wa.getApiKey().isBlank()) {
            return "WHATSAPP_SKIPPED (apiKey not configured – see setup instructions)";
        }

        try {
            String message = buildMessage(req);
            String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String url     = String.format(CALLMEBOT_URL,
                    URLEncoder.encode(targetPhone, StandardCharsets.UTF_8),
                    encoded,
                    wa.getApiKey());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("WhatsApp sent to {}", targetPhone);
                return "WHATSAPP_OK → " + targetPhone;
            } else {
                log.warn("CallMeBot HTTP {}: {}", response.statusCode(), response.body());
                return "WHATSAPP_FAILED: HTTP " + response.statusCode() + " – " + response.body();
            }

        } catch (Exception e) {
            log.error("WhatsApp failed: {}", e.getMessage());
            return "WHATSAPP_FAILED: " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String buildMessage(NotificationRequest req) {
        String status   = req.isMatch() ? "✅ CONFIRMED MATCH" : "⚠️ POSSIBLE MATCH";
        String greeting = (req.getReporterName() != null && !req.getReporterName().isBlank())
                ? "Hello " + req.getReporterName() + "," : "Hello,";
        String missing  = (req.getMissingPersonName() != null && !req.getMissingPersonName().isBlank())
                ? req.getMissingPersonName() : "your family member";

        return String.format(
            """
            ❤️ *Safe Return Alert*
            
            %s
            %s
            
            We found a possible match for *%s*.
            
            *Matched Inmate:* %s
            *ID:* %s
            *Confidence:* %.1f%%
            *Location:* %s
            
            Please log in to Safe Return to verify and take action.
            """,
            greeting,
            status,
            missing,
            req.getPersonName(),
            req.getPersonId(),
            req.getConfidence(),
            req.getLocation() != null ? req.getLocation() : "Unknown"
        );
    }
}