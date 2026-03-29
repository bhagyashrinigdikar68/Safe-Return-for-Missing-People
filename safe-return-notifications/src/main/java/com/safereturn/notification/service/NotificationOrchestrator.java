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
 * Decision logic:
 *   confidence >= threshold (e.g. 70%) → send all enabled channels
 *   confidence < threshold             → skip (return early)
 */
@Service
public class NotificationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(NotificationOrchestrator.class);

    private final NotificationConfig config;
    private final EmailNotificationService emailService;
    private final NtfyNotificationService ntfyService;
    private final WhatsAppNotificationService whatsAppService;

    public NotificationOrchestrator(
            NotificationConfig config,
            EmailNotificationService emailService,
            NtfyNotificationService ntfyService,
            WhatsAppNotificationService whatsAppService) {
        this.config          = config;
        this.emailService    = emailService;
        this.ntfyService     = ntfyService;
        this.whatsAppService = whatsAppService;
    }

    /**
     * Entry point called by the REST controller.
     * Checks confidence threshold, then fires all channels concurrently.
     */
    public NotificationResult process(NotificationRequest req) {
        double threshold = config.getConfidenceThreshold();

        log.info("Notification request: person={} confidence={:.1f}% threshold={:.1f}%",
                req.getPersonName(), req.getConfidence(), threshold);

        // ── Threshold gate ────────────────────────────────────────────────────
        if (req.getConfidence() < threshold) {
            String msg = String.format(
                    "Confidence %.1f%% is below threshold %.1f%% – notification suppressed.",
                    req.getConfidence(), threshold);
            log.info(msg);
            return NotificationResult.error(msg);
        }

        // ── Fire all channels ─────────────────────────────────────────────────
        NotificationResult result = NotificationResult.ok(
                String.format("Notifications dispatched for %s (%.1f%% confidence)",
                        req.getPersonName(), req.getConfidence()));

        // Email
        String emailResult = emailService.send(req);
        result.addChannelResult(emailResult);
        log.info("Email channel: {}", emailResult);

        // ntfy.sh push notification
        String ntfyResult = ntfyService.send(req);
        result.addChannelResult(ntfyResult);
        log.info("ntfy channel: {}", ntfyResult);

        // WhatsApp (CallMeBot)
        String waResult = whatsAppService.send(req);
        result.addChannelResult(waResult);
        log.info("WhatsApp channel: {}", waResult);

        return result;
    }
}
