"""
notification_client.py  (FIXED – user-specific notifications)
─────────────────────────────────────────────────────────────────────────────
Drop this file next to your api.py.

KEY FIX:  notify_if_match() now accepts family_email, family_phone,
          reporter_name, and missing_person_name.
          These are forwarded to Spring Boot so each match notification
          goes to the SPECIFIC family who filed the report —
          not the hardcoded global email in application.yml.

Usage:
    from notification_client import notify_if_match
    notify_if_match(
        result_dict,
        location        = "Main Gate Camera",
        family_email    = "ramesh@gmail.com",   # from the report record
        family_phone    = "+919876543210",       # from the report record
        reporter_name   = "Ramesh Kumar",        # from the report record
        missing_person_name = "Rajesh Kumar"     # from the report record
    )
─────────────────────────────────────────────────────────────────────────────
"""

import logging
import requests
from datetime import datetime

log = logging.getLogger("safe_return.notifications")

NOTIFICATION_SERVICE_URL = "http://localhost:8080/api/notify"
CONFIDENCE_THRESHOLD     = 70.0          # must match application.yml


def notify_if_match(
    recognize_result:    dict,
    location:            str  = "Unknown Camera",
    family_email:        str  = None,    # NEW: reporter's email from DB/report
    family_phone:        str  = None,    # NEW: reporter's phone from DB/report
    reporter_name:       str  = None,    # NEW: reporter's display name
    missing_person_name: str  = None,    # NEW: missing person's name from report
) -> bool:
    """
    Call after a successful /recognize response.
    Sends a notification to Spring Boot if confidence >= CONFIDENCE_THRESHOLD.

    Args:
        recognize_result:     dict returned by /recognize
        location:             camera / location label
        family_email:         email of the person who FILED the report
                              → notification is sent specifically to this address
        family_phone:         phone of the reporter (for WhatsApp)
        reporter_name:        display name used in email greeting
        missing_person_name:  name of the missing person (from the report)

    Returns:
        True if notification was dispatched, False otherwise.
    """
    try:
        confidence = float(recognize_result.get("confidence", 0))

        if confidence < CONFIDENCE_THRESHOLD:
            log.debug(
                "Confidence %.1f%% below threshold %.1f%% – skipping notification",
                confidence, CONFIDENCE_THRESHOLD
            )
            return False

        # ── Build payload with all user-specific fields ────────────────────────
        payload = {
            # Face recognition fields (original)
            "person_id":    recognize_result.get("person_id", ""),
            "person_name":  recognize_result.get("person_name", "Unknown"),
            "confidence":   confidence,
            "distance":     recognize_result.get("distance", 0),
            "threshold":    recognize_result.get("threshold", 0),
            "match":        recognize_result.get("match", False),
            "location":     location,
            "timestamp":    datetime.now().isoformat(),

            # User-specific fields (NEW) ───────────────────────────────────────
            # Spring Boot routes the email/WhatsApp to these addresses
            # If None, they are omitted from JSON and Spring Boot falls back to config
            **({"family_email":        family_email}        if family_email        else {}),
            **({"family_phone":        family_phone}        if family_phone        else {}),
            **({"reporter_name":       reporter_name}       if reporter_name       else {}),
            **({"missing_person_name": missing_person_name} if missing_person_name else {}),
        }

        log.info(
            "Sending notification for '%s' (%.1f%%) → family_email=%s",
            payload["person_name"], confidence,
            family_email or "(config fallback)"
        )

        response = requests.post(
            NOTIFICATION_SERVICE_URL,
            json=payload,
            timeout=10
        )

        if response.status_code == 200:
            data = response.json()
            log.info(
                "Notification dispatched: %s → channels: %s | notified_email: %s",
                payload["person_name"],
                data.get("channelResults", []),
                data.get("notifiedEmail", "—")
            )
            return True
        else:
            log.warning(
                "Notification service returned HTTP %d: %s",
                response.status_code, response.text
            )
            return False

    except requests.exceptions.ConnectionError:
        log.warning(
            "Notification service not reachable at %s – continuing without notification",
            NOTIFICATION_SERVICE_URL
        )
        return False
    except Exception as e:
        log.error("Unexpected error sending notification: %s", e)
        return False


# ─────────────────────────────────────────────────────────────────────────────
#  HOW TO INTEGRATE INTO YOUR EXISTING api.py /recognize ROUTE
# ─────────────────────────────────────────────────────────────────────────────
#
#  BEFORE (old, sends to hardcoded config email):
#
#      result = { "success": True, "match": True, "person_name": ..., ... }
#      notify_if_match(result, location="Main Gate Camera")
#      return jsonify(result)
#
#
#  AFTER (fixed, sends to the reporter who filed this report):
#
#      # Look up the original report to get the reporter's contact details.
#      # How you query depends on your DB – MongoDB example shown:
#      report = db.reports.find_one({"person_id": person_id})
#
#      result = {
#          "success":       True,
#          "match":         matched_above_threshold,
#          "person_name":   person_name,
#          "person_id":     person_id,
#          "confidence":    confidence_pct,
#          "confidence_raw": confidence,
#          "distance":      distance,
#          "threshold":     threshold,
#          "matched_image": matched_b64,
#          "low_confidence": not matched_above_threshold,
#      }
#
#      notify_if_match(
#          result,
#          location             = "Main Gate Camera",
#          family_email         = report.get("contact_email") if report else None,
#          family_phone         = report.get("contact_phone") if report else None,
#          reporter_name        = report.get("contact_name")  if report else None,
#          missing_person_name  = report.get("full_name")     if report else None,
#      )
#      return jsonify(result)
#
#
#  The field names (contact_email, contact_phone, etc.) should match whatever
#  your MongoDB report document uses. Check your existing report submission
#  route to confirm the exact field names stored.
# ─────────────────────────────────────────────────────────────────────────────