"""
notification_client.py  –  Safe Return Notification Client
Calls Spring Boot /api/notify after a successful /recognize response.
Supports user-specific email/phone so each family gets their own alert.
"""

import logging
import requests
from datetime import datetime

log = logging.getLogger("safe_return.notifications")

NOTIFICATION_SERVICE_URL = "https://safe-return-notifications.onrender.com/api/notify"
CONFIDENCE_THRESHOLD     = 70.0


def notify_if_match(
    recognize_result:    dict,
    location:            str  = "Unknown Camera",
    family_email:        str  = None,
    family_phone:        str  = None,
    reporter_name:       str  = None,
    missing_person_name: str  = None,
) -> bool:
    """
    Call after a successful /recognize response.
    Sends notification to Spring Boot if confidence >= CONFIDENCE_THRESHOLD.
    """
    try:
        confidence = float(recognize_result.get("confidence", 0))

        if confidence < CONFIDENCE_THRESHOLD:
            log.debug(
                "Confidence %.1f%% below threshold %.1f%% – skipping notification",
                confidence, CONFIDENCE_THRESHOLD
            )
            return False

        payload = {
            "person_id":    recognize_result.get("person_id", ""),
            "person_name":  recognize_result.get("person_name", "Unknown"),
            "confidence":   confidence,
            "distance":     recognize_result.get("distance", 0),
            "threshold":    recognize_result.get("threshold", 0),
            "match":        recognize_result.get("match", False),
            "location":     location,
            "timestamp":    datetime.now().isoformat(),

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