package com.safereturn.notification.service;

import com.safereturn.notification.config.NotificationConfig;
import com.safereturn.notification.model.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

@Service
public class NtfyNotificationService {

    private static final Logger log = LoggerFactory.getLogger(NtfyNotificationService.class);
    private final NotificationConfig config;
    private final HttpClient httpClient;

    public NtfyNotificationService(NotificationConfig config) {
        this.config = config;
        HttpClient client;
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .sslContext(sslContext)
                    .build();
        } catch (Exception e) {
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
        this.httpClient = client;
    }

    public String send(NotificationRequest req) {
        if (!config.getNtfy().isEnabled()) return "NTFY_SKIPPED (disabled)";
        try {
            String topicUrl = config.getNtfy().getTopicUrl();
            String body = buildMessage(req);
            String title = buildTitle(req);
            String priority = req.isMatch() ? "high" : "default";
            String tags = req.isMatch() ? "rotating_light,face,family" : "warning,face";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(topicUrl))
                    .header("Title", title)
                    .header("Priority", priority)
                    .header("Tags", tags)
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .header("Actions", "view, Open Dashboard, " + config.getFlaskApiUrl())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("ntfy.sh notification sent to topic: {}", config.getNtfy().getTopic());
                return "NTFY_OK -> topic:" + config.getNtfy().getTopic();
            } else {
                return "NTFY_FAILED: HTTP " + response.statusCode();
            }
        } catch (Exception e) {
            log.error("ntfy.sh notification failed: {}", e.getMessage());
            return "NTFY_FAILED: " + e.getMessage();
        }
    }

    private String buildTitle(NotificationRequest req) {
        return req.isMatch() ? "Safe Return - MATCH FOUND" : "Safe Return - Possible Match";
    }

    private String buildMessage(NotificationRequest req) {
        String location = req.getLocation() != null ? req.getLocation() : "Unknown location";
        return String.format("Person: %s (ID: %s)\nConfidence: %.1f%%\nLocation: %s\nStatus: %s",
                req.getPersonName(), req.getPersonId(), req.getConfidence(), location,
                req.isMatch() ? "Confirmed Match" : "Possible Match - verify manually");
    }
}