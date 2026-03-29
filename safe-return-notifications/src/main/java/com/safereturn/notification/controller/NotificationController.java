package com.safereturn.notification.controller;

import com.safereturn.notification.config.NotificationConfig;
import com.safereturn.notification.model.NotificationRequest;
import com.safereturn.notification.model.NotificationResult;
import com.safereturn.notification.service.NotificationOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API endpoints consumed by the Flask face-recognition backend.
 *
 * Base URL: http://localhost:8080
 *
 * POST /api/notify        ← main endpoint, called by Flask after /recognize
 * POST /api/test          ← test endpoint with a dummy payload (no image needed)
 * GET  /api/health        ← health check
 * GET  /api/config        ← view current config (for debugging)
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")   // allow Flask (localhost:5000) to call this
public class NotificationController {

    private final NotificationOrchestrator orchestrator;
    private final NotificationConfig config;

    public NotificationController(NotificationOrchestrator orchestrator,
                                  NotificationConfig config) {
        this.orchestrator = orchestrator;
        this.config       = config;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  POST /api/notify
    //
    //  Called by Flask api.py after a successful /recognize response.
    //  Flask should call this whenever confidence >= 70 (or your threshold).
    //
    //  Request body (JSON):
    //  {
    //    "person_id":   "SR-2024-001",
    //    "person_name": "Rajesh Kumar",
    //    "confidence":  87.34,
    //    "distance":    0.1266,
    //    "threshold":   0.40,
    //    "match":       true,
    //    "location":    "Main Gate Camera",
    //    "timestamp":   "2024-05-15T14:32:00"
    //  }
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/notify")
    public ResponseEntity<NotificationResult> notify(
            @RequestBody NotificationRequest request) {

        NotificationResult result = orchestrator.process(request);
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  POST /api/test
    //  Quick smoke-test without needing a real face scan.
    //  curl -X POST http://localhost:8080/api/test
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/test")
    public ResponseEntity<NotificationResult> test() {
        NotificationRequest dummy = new NotificationRequest();
        dummy.setPersonId("TEST-001");
        dummy.setPersonName("Test Person");
        dummy.setConfidence(87.5);
        dummy.setDistance(0.125);
        dummy.setThreshold(0.40);
        dummy.setMatch(true);
        dummy.setLocation("Test Camera");
        dummy.setTimestamp(java.time.LocalDateTime.now().toString());

        NotificationResult result = orchestrator.process(dummy);
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GET /api/health
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "service", "Safe Return Notification Service",
                "version", "1.0.0"
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GET /api/config  – show active settings (omits passwords)
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> showConfig() {
        return ResponseEntity.ok(Map.of(
                "confidenceThreshold", config.getConfidenceThreshold(),
                "emailEnabled",        config.getEmail().isEnabled(),
                "emailRecipients",     config.getEmail().getFamilyEmailList(),
                "ntfyEnabled",         config.getNtfy().isEnabled(),
                "ntfyTopic",           config.getNtfy().getTopic(),
                "whatsappEnabled",     config.getWhatsapp().isEnabled()
        ));
    }
}
