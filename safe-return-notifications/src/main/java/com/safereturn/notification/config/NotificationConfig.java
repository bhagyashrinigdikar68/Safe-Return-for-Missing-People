package com.safereturn.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "notification")
public class NotificationConfig {

    private double confidenceThreshold = 70.0;
    private EmailConfig email = new EmailConfig();
    private NtfyConfig ntfy = new NtfyConfig();
    private WhatsAppConfig whatsapp = new WhatsAppConfig();
    private SmsConfig sms = new SmsConfig();
    private String flaskApiUrl = "http://localhost:5000";

    // ── Nested config classes ────────────────────────────────────────────────

    public static class EmailConfig {
        private boolean enabled = true;
        private String from;
        private String fromName = "Safe Return System";
        /** Raw comma-separated string from YAML / env var */
        private String familyEmails = "";

        public List<String> getFamilyEmailList() {
            if (familyEmails == null || familyEmails.isBlank()) return List.of();
            return Arrays.stream(familyEmails.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }

        public String getFromName() { return fromName; }
        public void setFromName(String fromName) { this.fromName = fromName; }

        public String getFamilyEmails() { return familyEmails; }
        public void setFamilyEmails(String familyEmails) { this.familyEmails = familyEmails; }
    }

    public static class NtfyConfig {
        private boolean enabled = true;
        private String server = "https://ntfy.sh";
        private String topic = "safe-return-default";

        public String getTopicUrl() {
            return server.stripTrailing() + "/" + topic;
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getServer() { return server; }
        public void setServer(String server) { this.server = server; }

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
    }

    public static class WhatsAppConfig {
        private boolean enabled = false;
        private String phone;
        private String apiKey;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    // ── SMS (Twilio) config ───────────────────────────────────────────────────
    public static class SmsConfig {
        private boolean enabled    = false;
        private String  accountSid = "";
        private String  authToken  = "";
        private String  fromNumber = "";   // your Twilio number, e.g. +12065550100
        private String  toPhone    = "";   // fallback if no family_phone in request

        public boolean isEnabled()              { return enabled; }
        public void    setEnabled(boolean v)    { this.enabled = v; }

        public String  getAccountSid()          { return accountSid; }
        public void    setAccountSid(String v)  { this.accountSid = v; }

        public String  getAuthToken()           { return authToken; }
        public void    setAuthToken(String v)   { this.authToken = v; }

        public String  getFromNumber()          { return fromNumber; }
        public void    setFromNumber(String v)  { this.fromNumber = v; }

        public String  getToPhone()             { return toPhone; }
        public void    setToPhone(String v)     { this.toPhone = v; }
    }

    // ── Root fields ───────────────────────────────────────────────────────────

    // ── Root getters / setters ────────────────────────────────────────────────

    public double getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(double confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }

    public EmailConfig getEmail() { return email; }
    public void setEmail(EmailConfig email) { this.email = email; }

    public NtfyConfig getNtfy() { return ntfy; }
    public void setNtfy(NtfyConfig ntfy) { this.ntfy = ntfy; }

    public WhatsAppConfig getWhatsapp() { return whatsapp; }
    public void setWhatsapp(WhatsAppConfig whatsapp) { this.whatsapp = whatsapp; }

    public SmsConfig getSms() { return sms; }
    public void setSms(SmsConfig sms) { this.sms = sms; }

    public String getFlaskApiUrl() { return flaskApiUrl; }
    public void setFlaskApiUrl(String flaskApiUrl) { this.flaskApiUrl = flaskApiUrl; }
}
