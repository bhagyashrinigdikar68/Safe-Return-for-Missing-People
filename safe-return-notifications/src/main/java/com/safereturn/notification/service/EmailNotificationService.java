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
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final NotificationConfig config;

    public EmailNotificationService(JavaMailSender mailSender,
                                    NotificationConfig config) {
        this.mailSender = mailSender;
        this.config = config;
    }

    /**
     * Send HTML email to all configured family addresses.
     *
     * @return "EMAIL_OK" or "EMAIL_FAILED: <reason>"
     */
    public String send(NotificationRequest req) {
        if (!config.getEmail().isEnabled()) {
            return "EMAIL_SKIPPED (disabled)";
        }

        List<String> recipients = config.getEmail().getFamilyEmailList();
        if (recipients.isEmpty()) {
            log.warn("No family email recipients configured.");
            return "EMAIL_SKIPPED (no recipients)";
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(config.getEmail().getFrom(),
                           config.getEmail().getFromName());
            helper.setTo(recipients.toArray(new String[0]));
            helper.setSubject(buildSubject(req));
            helper.setText(buildHtmlBody(req), true);  // true = isHtml

            mailSender.send(msg);
            log.info("Email sent to {} recipients for person: {}",
                     recipients.size(), req.getPersonName());
            return "EMAIL_OK → " + recipients;

        } catch (Exception e) {
            log.error("Email send failed: {}", e.getMessage());
            return "EMAIL_FAILED: " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String buildSubject(NotificationRequest req) {
        String matchType = req.isMatch() ? "✅ MATCH FOUND" : "⚠️ POSSIBLE MATCH";
        return String.format("[Safe Return] %s – %s (%.1f%% confidence)",
                matchType, req.getPersonName(), req.getConfidence());
    }

    private String buildHtmlBody(NotificationRequest req) {
        String matchBadge = req.isMatch()
                ? "<span style='background:#16a34a;color:white;padding:4px 12px;border-radius:4px;font-weight:bold;'>✅ MATCH</span>"
                : "<span style='background:#f59e0b;color:white;padding:4px 12px;border-radius:4px;font-weight:bold;'>⚠️ POSSIBLE MATCH</span>";

        String timestamp = req.getTimestamp() != null
                ? req.getTimestamp()
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String location = req.getLocation() != null ? req.getLocation() : "Unknown";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8"/>
                  <style>
                    body { font-family: Arial, sans-serif; background: #f3f4f6; margin: 0; padding: 20px; }
                    .card { background: white; border-radius: 12px; padding: 32px; max-width: 560px;
                            margin: 0 auto; box-shadow: 0 4px 20px rgba(0,0,0,0.08); }
                    .header { text-align: center; margin-bottom: 24px; }
                    .header h1 { color: #1e3a5f; font-size: 22px; margin: 0; }
                    .header p { color: #6b7280; font-size: 13px; margin: 4px 0 0; }
                    table { width: 100%%; border-collapse: collapse; margin-top: 20px; }
                    td { padding: 10px 14px; border-bottom: 1px solid #e5e7eb; font-size: 14px; }
                    td:first-child { color: #6b7280; width: 40%%; font-weight: 600; }
                    td:last-child { color: #111827; }
                    .confidence-bar { background:#e5e7eb; border-radius:99px; height:10px; margin-top:6px; }
                    .confidence-fill { background:#16a34a; border-radius:99px; height:10px;
                                       width: %.1f%%; }
                    .footer { text-align:center; font-size:12px; color:#9ca3af; margin-top:28px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="header">
                      <h1>🔍 Safe Return – Face Match Alert</h1>
                      <p>Automated notification from the Safe Return face recognition system</p>
                    </div>

                    <div style="text-align:center; margin: 16px 0;">%s</div>

                    <table>
                      <tr><td>Person Name</td><td><strong>%s</strong></td></tr>
                      <tr><td>Person ID</td><td>%s</td></tr>
                      <tr><td>Confidence</td>
                          <td>
                            <strong>%.1f%%</strong>
                            <div class="confidence-bar">
                              <div class="confidence-fill"></div>
                            </div>
                          </td>
                      </tr>
                      <tr><td>Distance Score</td><td>%.4f (threshold: %.4f)</td></tr>
                      <tr><td>Location</td><td>%s</td></tr>
                      <tr><td>Detected At</td><td>%s</td></tr>
                    </table>

                    <div class="footer">
                      <p>Safe Return System &bull; Automated Alert &bull; Do not reply to this email</p>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                req.getConfidence(),   // confidence-fill width
                matchBadge,
                req.getPersonName(),
                req.getPersonId(),
                req.getConfidence(),
                req.getConfidence(),   // confidence bar duplicate (formatted twice)
                req.getDistance(),
                req.getThreshold(),
                location,
                timestamp
        );
    }
}
