package com.safereturn.notification.service;

import com.safereturn.notification.config.NotificationConfig;
import com.safereturn.notification.model.NotificationRequest;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends rich HTML email alerts via Gmail SMTP (100% free).
 *
 * ── KEY FIX ──────────────────────────────────────────────────────────────────
 * BEFORE: always sent to config.getEmail().getFamilyEmailList()
 *         → every match went to "bnigdikar@gmail.com" regardless of who reported
 *
 * AFTER:  if request.getFamilyEmail() is present → send ONLY to that address
 *         if not present                          → fall back to config list
 *
 * This means user A's match goes to user A's email, user B's to user B's.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender    mailSender;
    private final NotificationConfig config;

    public EmailNotificationService(JavaMailSender mailSender, NotificationConfig config) {
        this.mailSender = mailSender;
        this.config     = config;
    }

    /**
     * Send HTML email to the appropriate recipient(s).
     *
     * Priority:
     *   1. req.getFamilyEmail()  ← specific user who filed the report   [NEW]
     *   2. config family-emails  ← global fallback list from application.yml
     *
     * @return "EMAIL_OK → <address>" or "EMAIL_FAILED: <reason>"
     */
    public String send(NotificationRequest req) {

        if (!config.getEmail().isEnabled()) {
            return "EMAIL_SKIPPED (disabled)";
        }

        // ── Resolve recipient list ────────────────────────────────────────────
        List<String> recipients;

        if (req.hasSpecificRecipient()) {
            // User-specific: only notify the person who filed THIS report
            recipients = List.of(req.getFamilyEmail());
            log.info("Using user-specific email: {}", req.getFamilyEmail());
        } else {
            // Fallback: global list from application.yml
            recipients = config.getEmail().getFamilyEmailList();
            log.warn("No family_email in request – falling back to config list: {}", recipients);
        }

        if (recipients.isEmpty()) {
            log.warn("No email recipients resolved for person: {}", req.getPersonName());
            return "EMAIL_SKIPPED (no recipients configured)";
        }

        // ── Send ─────────────────────────────────────────────────────────────
        try {
            MimeMessage    msg    = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(config.getEmail().getFrom(), config.getEmail().getFromName());
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(buildSubject(req));
            helper.setText(buildHtmlBody(req), true);

            mailSender.send(msg);

            log.info("Email sent to {} for match: {} ({:.1f}%)",
                     recipients, req.getPersonName(), req.getConfidence());
            return "EMAIL_OK → " + recipients;

        } catch (Exception e) {
            log.error("Email send failed: {}", e.getMessage());
            return "EMAIL_FAILED: " + e.getMessage();
        }
    }

    // ── Email content builders ────────────────────────────────────────────────

    private String buildSubject(NotificationRequest req) {
        String matchType = req.isMatch() ? "✅ MATCH FOUND" : "⚠️ POSSIBLE MATCH";
        // If we know the missing person's name, use it in subject — more personal
        String subjectName = (req.getMissingPersonName() != null && !req.getMissingPersonName().isBlank())
                ? req.getMissingPersonName()
                : req.getPersonName();
        return String.format("[Safe Return] %s – %s (%.1f%% confidence)",
                matchType, subjectName, req.getConfidence());
    }

    private String buildHtmlBody(NotificationRequest req) {
        String matchBadge = req.isMatch()
                ? "<span style='background:#16a34a;color:white;padding:4px 14px;border-radius:6px;font-weight:bold;font-size:14px;'>✅ CONFIRMED MATCH</span>"
                : "<span style='background:#f59e0b;color:white;padding:4px 14px;border-radius:6px;font-weight:bold;font-size:14px;'>⚠️ POSSIBLE MATCH</span>";

        String timestamp = req.getTimestamp() != null
                ? req.getTimestamp()
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss"));

        // Personalised greeting
        String greeting = (req.getReporterName() != null && !req.getReporterName().isBlank())
                ? "Dear " + req.getReporterName() + ","
                : "Dear Family,";

        // Which person are we referring to in body text
        String missingName = (req.getMissingPersonName() != null && !req.getMissingPersonName().isBlank())
                ? req.getMissingPersonName()
                : "your missing family member";

        String confidenceBar = "<div style='background:#e5e7eb;border-radius:99px;height:12px;margin-top:6px;'>"
                + "<div style='background:#16a34a;border-radius:99px;height:12px;width:"
                + Math.min(100, req.getConfidence()) + "%;'></div></div>";

        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"/>
                <style>
                  body{font-family:Arial,sans-serif;background:#f3f4f6;margin:0;padding:20px;}
                  .card{background:white;border-radius:14px;padding:36px;max-width:580px;margin:0 auto;box-shadow:0 4px 20px rgba(0,0,0,.09);}
                  .header{text-align:center;border-bottom:2px solid #f3f4f6;padding-bottom:20px;margin-bottom:24px;}
                  .logo{font-size:28px;font-weight:900;color:#f97316;margin-bottom:4px;}
                  .sub{color:#6b7280;font-size:13px;}
                  table{width:100%%;border-collapse:collapse;margin-top:20px;}
                  td{padding:11px 14px;border-bottom:1px solid #f3f4f6;font-size:14px;}
                  td:first-child{color:#6b7280;width:38%%;font-weight:600;}
                  td:last-child{color:#111827;font-weight:500;}
                  .footer{text-align:center;font-size:12px;color:#9ca3af;margin-top:28px;padding-top:20px;border-top:1px solid #f3f4f6;}
                  .action-btn{display:inline-block;margin-top:20px;padding:12px 28px;background:#f97316;color:white;border-radius:8px;text-decoration:none;font-weight:bold;font-size:15px;}
                </style>
                </head>
                <body>
                  <div class="card">
                    <div class="header">
                      <div class="logo">❤️ Safe Return</div>
                      <div class="sub">Automated Face Recognition Alert</div>
                    </div>

                    <p style="font-size:15px;color:#374151;margin:0 0 16px;">%s</p>
                    <p style="font-size:14px;color:#374151;">
                      We have found a possible match for <strong>%s</strong>
                      in our shelter database. Please review the details below.
                    </p>

                    <div style="text-align:center;margin:20px 0;">%s</div>

                    <table>
                      <tr><td>Matched Inmate Name</td><td><strong>%s</strong></td></tr>
                      <tr><td>Inmate ID</td><td>%s</td></tr>
                      <tr><td>Confidence</td>
                          <td><strong>%.1f%%</strong>%s</td></tr>
                      <tr><td>Distance Score</td><td>%.4f (threshold: %.4f)</td></tr>
                      <tr><td>Camera / Location</td><td>%s</td></tr>
                      <tr><td>Detected At</td><td>%s</td></tr>
                    </table>

                    <div style="text-align:center;">
                      <a href="http://localhost:3000/dashboard" class="action-btn">
                        View on Safe Return Dashboard →
                      </a>
                    </div>

                    <div class="footer">
                      Safe Return System &bull; This is an automated alert &bull; Do not reply
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                greeting,
                missingName,
                matchBadge,
                req.getPersonName(),
                req.getPersonId(),
                req.getConfidence(),
                confidenceBar,
                req.getDistance(),
                req.getThreshold(),
                req.getLocation() != null ? req.getLocation() : "Unknown",
                timestamp
        );
    }
}