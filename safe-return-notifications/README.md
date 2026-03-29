# Safe Return – Notification Service

A **100% free** Spring Boot notification system that alerts family members when a face match is detected with ≥70% confidence.

## Free Channels Included

| Channel | Service | Cost | Setup Time |
|---------|---------|------|------------|
| 📧 Email | Gmail SMTP | **Free** | 5 min |
| 📱 Push (phone) | ntfy.sh | **Free** | 2 min |
| 💬 WhatsApp | CallMeBot API | **Free** | 5 min |

---

## Architecture

```
Camera / Frontend
       │
       ▼
Flask API (api.py) ──── /recognize ────► face match result
       │
       │  confidence ≥ 70%?
       ▼
notification_client.py
       │
       ▼  HTTP POST /api/notify
Spring Boot (port 8080)
       │
       ├──► Gmail SMTP ──────────────► Family Email (rich HTML)
       ├──► ntfy.sh ─────────────────► Family Phone App (push)
       └──► CallMeBot WhatsApp ──────► Family WhatsApp
```

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Gmail account
- Python `requests` library in your Flask env (`pip install requests`)

---

## Setup Guide

### Step 1 – Gmail SMTP (Email notifications)

1. Log into the Gmail account you want to send alerts FROM
2. Go to **Google Account → Security → 2-Step Verification** → Enable it
3. Go to **Google Account → Security → App Passwords**
4. Create an App Password (select "Mail" + "Other") → copy the 16-character password
5. Set environment variables (or edit `application.yml`):
   ```bash
   export GMAIL_USER=your-sending-account@gmail.com
   export GMAIL_APP_PASSWORD=abcd efgh ijkl mnop    # 16-char app password
   export FAMILY_EMAILS=mom@gmail.com,dad@yahoo.com  # comma-separated
   ```

---

### Step 2 – ntfy.sh (Free Push Notifications to Phone)

No account needed!

1. Install the **ntfy** app on family member phones:
   - Android: [Play Store](https://play.google.com/store/apps/details?id=io.heckel.ntfy)
   - iOS: [App Store](https://apps.apple.com/app/ntfy/id1625396347)
2. Open app → tap **"+"** → subscribe to your unique topic name
   - Pick something unique: `safe-return-yourfamilyname-2024`
3. Set the topic in your environment:
   ```bash
   export NTFY_TOPIC=safe-return-yourfamilyname-2024
   ```
4. All family members subscribe to the SAME topic on their phones ✅

---

### Step 3 – WhatsApp (Optional, CallMeBot)

1. Save **+34 644 52 74 97** in contacts as "CallMeBot"
2. Send this exact message via WhatsApp to CallMeBot:
   ```
   I allow callmebot to send me messages
   ```
3. Wait ~2 minutes → you'll receive your API key
4. Configure:
   ```bash
   export WHATSAPP_PHONE=+919876543210   # family member's number with country code
   export WHATSAPP_API_KEY=12345678      # key from CallMeBot
   ```
5. Set `notification.whatsapp.enabled: true` in `application.yml`

**Note:** ~38 free messages/day per phone number. More than enough for alerts.

---

### Step 4 – Build and Run the Spring Boot Service

```bash
cd safe-return-notifications
mvn clean package -DskipTests
java -jar target/notification-service-1.0.0.jar
```

Or with environment variables inline:
```bash
GMAIL_USER=you@gmail.com \
GMAIL_APP_PASSWORD="abcd efgh ijkl mnop" \
FAMILY_EMAILS="mom@gmail.com,dad@gmail.com" \
NTFY_TOPIC="safe-return-yourfamily-2024" \
java -jar target/notification-service-1.0.0.jar
```

Service starts on **http://localhost:8080**

---

### Step 5 – Integrate with Your Flask api.py

1. Copy `notification_client.py` to the same folder as your `api.py`
2. Add import at the top of `api.py`:
   ```python
   from notification_client import notify_if_match
   ```
3. In the `/recognize` route, change the final `return jsonify(...)` to:
   ```python
   result = {
       "success":        True,
       "match":          matched_above_threshold,
       "person_name":    person_name,
       "person_id":      person_id,
       "confidence":     confidence_pct,
       "confidence_raw": confidence,
       "distance":       distance,
       "threshold":      threshold,
       "matched_image":  matched_b64,
       "low_confidence": not matched_above_threshold,
   }
   notify_if_match(result, location="Main Gate Camera")  # ← ADD THIS LINE
   return jsonify(result)
   ```

That's it! The notification client is **non-blocking** — if Spring Boot is down, your face recognition still works normally.

---

## Test the Notification Service

Without any face scan, fire a dummy notification:
```bash
curl -X POST http://localhost:8080/api/test
```

Or send a custom payload:
```bash
curl -X POST http://localhost:8080/api/notify \
  -H "Content-Type: application/json" \
  -d '{
    "person_id": "SR-2024-001",
    "person_name": "Rajesh Kumar",
    "confidence": 87.5,
    "distance": 0.125,
    "threshold": 0.40,
    "match": true,
    "location": "Main Gate Camera"
  }'
```

Check health:
```bash
curl http://localhost:8080/api/health
```

View active config:
```bash
curl http://localhost:8080/api/config
```

---

## Notification Threshold Logic

| Confidence | Action |
|------------|--------|
| ≥ 70% | ✅ Send notifications to all channels |
| < 70% | ❌ Suppressed – not enough confidence |

Change the threshold in `application.yml`:
```yaml
notification:
  confidence-threshold: 70.0   # change to 75.0, 80.0, etc.
```

---

## Project Structure

```
safe-return-notifications/
├── pom.xml
├── notification_client.py          ← Copy to Flask project folder
└── src/main/
    ├── java/com/safereturn/notification/
    │   ├── NotificationServiceApplication.java
    │   ├── config/
    │   │   └── NotificationConfig.java
    │   ├── controller/
    │   │   └── NotificationController.java
    │   ├── model/
    │   │   ├── NotificationRequest.java
    │   │   └── NotificationResult.java
    │   └── service/
    │       ├── NotificationOrchestrator.java
    │       ├── EmailNotificationService.java
    │       ├── NtfyNotificationService.java
    │       └── WhatsAppNotificationService.java
    └── resources/
        └── application.yml
```
