from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
from pymongo import MongoClient
import os
from werkzeug.utils import secure_filename
import re
from bson import ObjectId
from datetime import datetime
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

app = Flask(__name__)
CORS(app)

MONGO_URI = "mongodb+srv://render_user:Vanshika0509@cluster0.6ds8ydm.mongodb.net/missing_person_db"
client = MongoClient(MONGO_URI)

try:
    client.server_info()
    print("✅ MongoDB Connected Successfully")
except Exception as e:
    print("❌ MongoDB Connection Failed:", e)

db                       = client["missing_person_db"]
collection               = db["user_login_details"]
notifications_collection = db["notifications"]

BASE_DIR      = os.path.dirname(os.path.abspath(__file__))
UPLOAD_FOLDER = os.path.join(BASE_DIR, "uploads", "photos")
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


@app.route("/submit", methods=["POST"])
def submit():
    data  = request.form
    photo = request.files.get("photo")

    # ── DEBUG: print every key Flask received from the form ──────
    print("\n========== /submit received keys ==========")
    for key in data.keys():
        print(f"  {key!r:35s} = {data.get(key)!r}")
    print("===========================================\n")

    # ── 1. Primary phone  (key = "public-familyPhone") ───────────
    phone_number = data.get("public-familyPhone", "").strip()
    if not re.match(r'^[6-9]\d{9}$', phone_number):
        return jsonify({"error": "Invalid Indian phone number (must be 10 digits and start with 6-9)"}), 400

    # ── 2. Alternate phone  (key = "public-familyPhone2") ────────
    alt_raw    = data.get("public-familyPhone2", "").strip()
    alt_digits = re.sub(r'\D', '', alt_raw)
    if alt_digits and not re.match(r'^[6-9]\d{9}$', alt_digits):
        return jsonify({"error": "Alternate phone invalid (10 digits, starts with 6-9)"}), 400

    # ── 3. Date  (key = "public-dateTime") ───────────────────────
    last_seen = data.get("public-dateTime", "").strip()
    if not last_seen:
        return jsonify({"error": "Last seen date & time is required."}), 400
    try:
        if datetime.fromisoformat(last_seen) > datetime.now():
            return jsonify({"error": "Future date is not allowed."}), 400
    except ValueError:
        return jsonify({"error": "Invalid date format."}), 400

    # ── 4. Family Aadhaar  (key = "public-familyAadhaar") ────────
    family_aadhaar = data.get("public-familyAadhaar", "").strip()
    if family_aadhaar and not re.match(r'^[2-9]\d{11}$', family_aadhaar):
        return jsonify({"error": "Family Aadhaar invalid — 12 digits, cannot start with 0 or 1."}), 400

    # ── 5. Person Aadhaar  (key = "public-personAadhaar") ────────
    person_aadhaar = data.get("public-personAadhaar", "").strip()
    if person_aadhaar and not re.match(r'^[2-9]\d{11}$', person_aadhaar):
        return jsonify({"error": "Person Aadhaar invalid — 12 digits, cannot start with 0 or 1."}), 400

    # ── 6. Email  (key = "public-familyEmail") ───────────────────
    contact_email = data.get("public-familyEmail", "").strip()

    # ── 7. Photo ──────────────────────────────────────────────────
    photo_path = None
    if photo and photo.filename:
        filename   = secure_filename(photo.filename)
        full_path  = os.path.join(UPLOAD_FOLDER, filename)
        photo.save(full_path)
        photo_path = "/uploads/photos/" + filename

    # ── 8. Build document ─────────────────────────────────────────
    document = {
        # original fields
        "full_name":            data.get("public-fullName",       ""),
        "age":                  data.get("public-age",            ""),
        "gender":               data.get("gender",                ""),
        "language_spoken":      data.get("language_spoken",       ""),
        "last_seen_location":   data.get("public-location",       ""),
        "last_seen_datetime":   last_seen,
        "clothing_description": data.get("clothing_description",  ""),
        "general_description":  data.get("general_description",   ""),
        "medical_condition":    data.get("medical_condition",     ""),
        "contact_name":         data.get("public-familyName",     ""),
        "contact_phone":        phone_number,
        # NEW fields
        "contact_email":        contact_email,
        "alternate_phone":      alt_digits,
        "family_aadhaar":       family_aadhaar,
        "person_aadhaar":       person_aadhaar,
        # meta
        "photo_path":           photo_path,
        "status":               "Missing",
        "submitted_at":         datetime.now().strftime("%d %b %Y, %I:%M %p")
    }

    # ── DEBUG: print what will be saved ──────────────────────────
    print("📦 Saving to MongoDB:")
    for k, v in document.items():
        print(f"  {k:25s} = {v!r}")
    print()

    collection.insert_one(document)
    return jsonify({"message": "Report submitted successfully"})


@app.route("/uploads/photos/<path:filename>")
def get_photo(filename):
    return send_from_directory(UPLOAD_FOLDER, filename)


@app.route("/get-missing-reports", methods=["GET"])
def get_missing_reports():
    reports = list(collection.find())
    for r in reports:
        r["_id"] = str(r["_id"])
    return jsonify(reports)


@app.route("/get-reports", methods=["GET"])
def get_reports():
    reports = list(collection.find())
    for r in reports:
        r["_id"]    = str(r["_id"])
        r["status"] = r.get("status", "Missing")
    return jsonify(reports)


@app.route("/mark-found/<report_id>", methods=["POST"])
def mark_found(report_id):
    collection.update_one(
        {"_id": ObjectId(report_id)},
        {"$set": {"status": "Found"}}
    )
    return jsonify({"message": "Marked as Found"})


@app.route("/save-notification", methods=["POST"])
def save_notification():
    data     = request.get_json()
    document = {
        "type":        data.get("type",        "match"),
        "title":       data.get("title",       "Match Found"),
        "message":     data.get("message",     ""),
        "report_name": data.get("report_name", ""),
        "phone":       data.get("phone",       ""),
        "user_email":  data.get("user_email",  ""),
        "read":        False,
        "time":        datetime.now().strftime("%d %b %Y, %I:%M %p")
    }
    notifications_collection.insert_one(document)
    return jsonify({"message": "Notification saved"})


@app.route("/get-notifications", methods=["GET"])
def get_notifications():
    notifs = list(notifications_collection.find().sort("_id", -1))
    for n in notifs:
        n["_id"] = str(n["_id"])
    return jsonify(notifs)


@app.route("/mark-notification-read/<notif_id>", methods=["POST"])
def mark_notification_read(notif_id):
    notifications_collection.update_one(
        {"_id": ObjectId(notif_id)},
        {"$set": {"read": True}}
    )
    return jsonify({"message": "Marked as read"})


@app.route("/delete-notification/<notif_id>", methods=["DELETE"])
def delete_notification(notif_id):
    notifications_collection.delete_one({"_id": ObjectId(notif_id)})
    return jsonify({"message": "Deleted"})



# ═══════════════════════════════════════════════════════════════
#  MATCH ALERT EMAIL  –  sends via Gmail SMTP (100% free)
#  Same Gmail account already used in app_Varuni.py for OTPs.
#  No new credentials needed.
# ═══════════════════════════════════════════════════════════════
ALERT_EMAIL_ADDRESS  = "vj8702889@gmail.com"
ALERT_EMAIL_PASSWORD = "ablrzdsjoshjemig"   # Gmail App Password (no spaces)


def _send_match_email(to_email, family_name, missing_name,
                      person_name, person_id, confidence, location):
    """Send HTML match-alert email via Gmail SMTP."""
    try:
        bar_width = min(100, int(confidence))
        subject   = f"[Safe Return] Match Found — {missing_name}"

        plain = (
            f"Hello {family_name},\n\n"
            f"We found a possible match for {missing_name}.\n\n"
            f"Matched Inmate : {person_name}\n"
            f"Inmate ID      : {person_id}\n"
            f"Confidence     : {confidence:.0f}%\n"
            f"Location       : {location}\n\n"
            f"Please log in to Safe Return to verify.\n\n"
            f"— Safe Return System"
        )

        html = f"""<!DOCTYPE html>
<html><head><meta charset="UTF-8"/>
<style>
@keyframes shimmer {{
  0%{{background-position:0% 50%}}
  100%{{background-position:200% 50%}}
}}
.sr-shine {{
  font-size:26px;font-weight:900;
  background:linear-gradient(90deg,#0B6623 0%,#4ade80 40%,#ffffff 50%,#4ade80 60%,#0B6623 100%);
  background-size:200% auto;
  -webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;
  animation:shimmer 2.5s linear infinite;
}}
</style>
</head>
<body style="margin:0;padding:20px;background:#f3f4f6;font-family:Arial,sans-serif;">
<div style="background:white;border-radius:14px;padding:36px;max-width:560px;margin:0 auto;box-shadow:0 4px 20px rgba(0,0,0,.09);">
  <div style="text-align:center;border-bottom:2px solid #f3f4f6;padding-bottom:18px;margin-bottom:22px;">
    <div class="sr-shine">SafeReturn❤️</div>
    <div style="color:#6b7280;font-size:13px;">Missing Person Alert System</div>
  </div>
  <p style="font-size:15px;color:#374151;margin:0 0 6px;">Dear {family_name},</p>
  <p style="font-size:14px;color:#374151;margin:0 0 18px;">
    We found a possible match for <strong>{missing_name}</strong> in our shelter database.
  </p>
  <div style="text-align:center;margin-bottom:18px;">
    <span style="background:#16a34a;color:white;padding:6px 20px;border-radius:6px;font-weight:bold;font-size:14px;">
      MATCH FOUND
    </span>
  </div>
  <table style="width:100%;border-collapse:collapse;background:#f8fafc;border-radius:10px;overflow:hidden;">
    <tr style="border-bottom:1px solid #e2e8f0;">
      <td style="padding:10px 14px;color:#64748b;font-size:.85rem;width:40%;font-weight:600;">Missing Person</td>
      <td style="padding:10px 14px;font-weight:700;color:#0f172a;">{missing_name}</td>
    </tr>
    <tr style="border-bottom:1px solid #e2e8f0;">
      <td style="padding:10px 14px;color:#64748b;font-size:.85rem;font-weight:600;">Matched Inmate</td>
      <td style="padding:10px 14px;font-weight:700;color:#0f172a;">{person_name}</td>
    </tr>
    <tr style="border-bottom:1px solid #e2e8f0;">
      <td style="padding:10px 14px;color:#64748b;font-size:.85rem;font-weight:600;">Inmate ID</td>
      <td style="padding:10px 14px;font-weight:600;color:#0f172a;">{person_id}</td>
    </tr>
    <tr style="border-bottom:1px solid #e2e8f0;">
      <td style="padding:10px 14px;color:#64748b;font-size:.85rem;font-weight:600;">Confidence</td>
      <td style="padding:10px 14px;">
        <strong style="color:#16a34a;">{confidence:.0f}%</strong>
        <div style="background:#e5e7eb;border-radius:99px;height:10px;margin-top:5px;">
          <div style="background:#16a34a;border-radius:99px;height:10px;width:{bar_width}%;"></div>
        </div>
      </td>
    </tr>
    <tr>
      <td style="padding:10px 14px;color:#64748b;font-size:.85rem;font-weight:600;">Detected At</td>
      <td style="padding:10px 14px;font-weight:600;color:#0f172a;">{location}</td>
    </tr>
  </table>
  <div style="text-align:center;font-size:12px;color:#9ca3af;margin-top:22px;padding-top:14px;border-top:1px solid #f3f4f6;">
    Safe Return System &bull; Automated alert &bull; Do not reply
  </div>
</div>
</body></html>"""

        msg = MIMEMultipart("alternative")
        msg["Subject"] = subject
        msg["From"]    = f"Safe Return <{ALERT_EMAIL_ADDRESS}>"
        msg["To"]      = to_email
        msg.attach(MIMEText(plain, "plain"))
        msg.attach(MIMEText(html,  "html"))

        with smtplib.SMTP("smtp.gmail.com", 587) as server:
            server.ehlo()
            server.starttls()
            server.ehlo()
            server.login(ALERT_EMAIL_ADDRESS, ALERT_EMAIL_PASSWORD)
            server.send_message(msg)

        return {"ok": True}
    except Exception as e:
        return {"ok": False, "error": str(e)}


@app.route("/send-match-alert", methods=["POST"])
def send_match_alert():
    """
    Called by script.js after every face-recognition match.
    Reads contact_email from the MongoDB report and emails the family.
    """
    data = request.get_json()
    if not data:
        return jsonify({"error": "No JSON body"}), 400

    report_id   = data.get("report_id",   "").strip()
    person_name = data.get("person_name", "Unknown")
    person_id   = data.get("person_id",   "")
    confidence  = float(data.get("confidence", 0))
    location    = data.get("location",    "Unknown Location")

    # Look up the original report by MongoDB _id
    report = None
    if report_id:
        try:
            report = collection.find_one({"_id": ObjectId(report_id)})
        except Exception as e:
            print(f"[EMAIL] Report lookup error: {e}")

    if not report:
        print(f"[EMAIL] Report not found: {report_id!r}")
        return jsonify({"error": f"Report not found: {report_id}"}), 404

    to_email     = (report.get("contact_email") or "").strip()
    family_name  = report.get("contact_name",  "Family")
    missing_name = report.get("full_name",      "your family member")

    if not to_email:
        print(f"[EMAIL] No contact_email on report {report_id}")
        return jsonify({"error": "No contact_email on this report"}), 400

    print(f"[EMAIL] Sending match alert to {to_email} for {missing_name}")
    result = _send_match_email(
        to_email     = to_email,
        family_name  = family_name,
        missing_name = missing_name,
        person_name  = person_name,
        person_id    = person_id,
        confidence   = confidence,
        location     = location
    )

    if result["ok"]:
        print(f"[EMAIL] Sent successfully to {to_email}")
        return jsonify({"message": f"Alert email sent to {to_email}"}), 200
    else:
        print(f"[EMAIL] Failed: {result['error']}")
        return jsonify({"error": result["error"]}), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=True)  # debug=True so prints show live