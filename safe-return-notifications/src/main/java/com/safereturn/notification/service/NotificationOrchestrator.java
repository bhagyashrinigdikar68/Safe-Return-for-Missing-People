package com.safereturn.notification.service;

import com.safereturn.notification.config.NotificationConfig;
import com.safereturn.notification.model.NotificationRequest;
import com.safereturn.notification.model.NotificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates all notification channels.
 *
 * ── FIXES ────────────────────────────────────────────────────────────────────
 * 1. Log format string used {:.1f} (Python syntax) → fixed to SLF4J {}.
 * 2. Result now records which email/phone was actually notified (for response).
 * 3. Logs the resolved recipient so you can confirm routing in console.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
public class NotificationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(NotificationOrchestrator.class);

    private final NotificationConfig          config;
    private final EmailNotificationService    emailService;
    private final NtfyNotificationService     ntfyService;
    private final WhatsAppNotificationService whatsAppService;

    public NotificationOrchestrator(
            NotificationConfig          config,
            EmailNotificationService    emailService,
            NtfyNotificationService     ntfyService,
            WhatsAppNotificationService whatsAppService) {
        this.config          = config;
        this.emailService    = emailService;
        this.ntfyService     = ntfyService;
        this.whatsAppService = whatsAppService;
    }

    /**
     * Entry point called by NotificationController.
     * Applies confidence gate, logs recipient routing, fires all channels.
     */
    public NotificationResult process(NotificationRequest req) {

        double threshold = config.getConfidenceThreshold();

        // FIX: was {:.1f} (Python syntax) — SLF4J uses {} placeholders only
        log.info("Notification request: person='{}' confidence={}% threshold={}% familyEmail='{}'",
                req.getPersonName(),
                String.format("%.1f", req.getConfidence()),
                String.format("%.1f", threshold),
                req.getFamilyEmail() != null ? req.getFamilyEmail() : "(none – will use config fallback)");

        // ── Confidence gate ───────────────────────────────────────────────────
        if (req.getConfidence() < threshold) {
            String msg = String.format(
                "Confidence %.1f%% below threshold %.1f%% – notification suppressed.",
                req.getConfidence(), threshold);
            log.info(msg);
            return NotificationResult.error(msg);
        }

        // ── Log routing decision ──────────────────────────────────────────────
        if (req.hasSpecificRecipient()) {
            log.info("Routing email to specific reporter: {}", req.getFamilyEmail());
        } else {
            log.warn("No family_email in request – routing to global config list");
        }

        NotificationResult result = NotificationResult.ok(
            String.format("Notifications dispatched for %s (%.1f%% confidence)",
                    req.getPersonName(), req.getConfidence()));

        // ── Email channel ─────────────────────────────────────────────────────
        String emailResult = emailService.send(req);
        result.addChannelResult(emailResult);
        // Record the actual email address notified in the response
        if (emailResult.startsWith("EMAIL_OK")) {
            result.setNotifiedEmail(
                req.hasSpecificRecipient()
                    ? req.getFamilyEmail()
                    : config.getEmail().getFamilyEmailList().toString());
        }
        log.info("Email channel: {}", emailResult);

        // ── ntfy.sh push channel ──────────────────────────────────────────────
        String ntfyResult = ntfyService.send(req);
        result.addChannelResult(ntfyResult);
        log.info("ntfy channel: {}", ntfyResult);

        // ── WhatsApp channel ──────────────────────────────────────────────────
        String waResult = whatsAppService.send(req);
        result.addChannelResult(waResult);
        if (waResult.startsWith("WHATSAPP_OK")) {
            result.setNotifiedPhone(
                req.hasSpecificPhone()
                    ? req.getFamilyPhone()
                    : config.getWhatsapp().getPhone());
        }
        log.info("WhatsApp channel: {}", waResult);

        return result;
    }
}