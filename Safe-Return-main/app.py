from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
from pymongo import MongoClient
import os
from werkzeug.utils import secure_filename
import re
from bson import ObjectId
from datetime import datetime

app = Flask(__name__)
CORS(app)

MONGO_URI = "mongodb+srv://render_user:Vanshika0509@cluster0.6ds8ydm.mongodb.net/missing_person_db"
client = MongoClient(MONGO_URI)

try:
    client.server_info()
    print("MongoDB Connected Successfully")
except Exception as e:
    print("MongoDB Connection Failed:", e)

db = client["missing_person_db"]
collection = db["user_login_details"]
notifications_collection = db["notifications"]

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
UPLOAD_FOLDER = os.path.join(BASE_DIR, "uploads", "photos")
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


# ── Submit lost person report (Public) ───────────────
@app.route("/submit", methods=["POST"])
def submit():
    data = request.form
    photo = request.files.get("photo")

    phone_number = data.get("public-familyPhone", "").strip()
    if not re.match(r'^[6-9]\d{9}$', phone_number):
        return jsonify({"error": "Invalid Indian phone number (must be 10 digits and start with 6-9)"}), 400

    last_seen = data.get("public-dateTime")
    if not last_seen:
        return jsonify({"error": "Last seen date & time is required."}), 400

    try:
        selected_date = datetime.fromisoformat(last_seen)
        if selected_date > datetime.now():
            return jsonify({"error": "Future date is not allowed."}), 400
    except ValueError:
        return jsonify({"error": "Invalid date format."}), 400

    # ── Aadhaar validation (optional fields) ──────────
    fam_aadhaar = data.get("family_aadhaar", "").strip()
    per_aadhaar = data.get("person_aadhaar", "").strip()

    if fam_aadhaar and not re.match(r'^[2-9]\d{11}$', fam_aadhaar):
        return jsonify({"error": "Invalid family Aadhaar number."}), 400
    if per_aadhaar and not re.match(r'^[2-9]\d{11}$', per_aadhaar):
        return jsonify({"error": "Invalid missing person Aadhaar number."}), 400

    # ── FIX: Save photo and store URL path (not OS path) ──
    photo_url = None
    if photo and photo.filename:
        filename = secure_filename(photo.filename)
        photo.save(os.path.join(UPLOAD_FOLDER, filename))
        photo_url = f"/uploads/photos/{filename}"      # ← relative URL, not OS path

    document = {
        "full_name":            data.get("public-fullName"),
        "age":                  data.get("public-age"),
        "gender":               data.get("gender"),
        "language_spoken":      data.get("language_spoken"),
        "last_seen_location":   data.get("public-location"),
        "last_seen_datetime":   data.get("public-dateTime"),
        "clothing_description": data.get("clothing_description"),
        "general_description":  data.get("general_description"),
        "medical_condition":    data.get("medical_condition"),
        "contact_name":         data.get("public-familyName"),
        "contact_phone":        phone_number,
        "contact_email":        data.get("contact_email", ""),
        # ── Aadhaar fields ──
        "family_aadhaar":       fam_aadhaar,
        "person_aadhaar":       per_aadhaar,
        # ── FIX: store URL path ──
        "photo_path":           photo_url,
        "status":               "Missing"
    }
    collection.insert_one(document)
    return jsonify({"message": "Report submitted successfully"})


# ── FIX: Serve uploaded photos correctly ─────────────
@app.route("/uploads/photos/<path:filename>")
def get_photo(filename):
    return send_from_directory(UPLOAD_FOLDER, filename)   # ← use absolute UPLOAD_FOLDER


# ── Submit inmate (Admin) ─────────────────────────────
@app.route("/submit-inmate", methods=["POST"])
def submit_inmate():
    data  = request.form
    photo = request.files.get("photo")

    photo_url = None
    if photo and photo.filename:
        filename = secure_filename(photo.filename)
        photo.save(os.path.join(UPLOAD_FOLDER, filename))
        photo_url = f"/uploads/photos/{filename}"

    document = {
        "inmate_id":       data.get("inmate_id"),
        "reg_no":          data.get("reg_no"),
        "full_name":       data.get("public-fullName"),
        "gender":          data.get("gender"),
        "language_spoken": data.get("language_spoken"),
        "address":         data.get("address"),
        "admin_status":    data.get("admin-status"),
        "joining_date":    data.get("joining_date"),
        "dob":             data.get("dob"),
        "photo_path":      photo_url,
        "status":          "Active",
        "created_at":      datetime.now().isoformat()
    }
    collection.insert_one(document)
    return jsonify({"message": "Inmate registered successfully"})


# ── Get all reports ───────────────────────────────────
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
        r["_id"] = str(r["_id"])
        r["status"] = r.get("status", "Missing")
    return jsonify(reports)


# ── Mark as found ─────────────────────────────────────
@app.route("/mark-found/<report_id>", methods=["POST"])
def mark_found(report_id):
    collection.update_one(
        {"_id": ObjectId(report_id)},
        {"$set": {"status": "Found"}}
    )
    return jsonify({"message": "Marked as Found"})


# ── Notifications ─────────────────────────────────────
@app.route("/save-notification", methods=["POST"])
def save_notification():
    data = request.get_json()
    document = {
        "type":        data.get("type", "match"),
        "title":       data.get("title", "Match Found"),
        "message":     data.get("message", ""),
        "report_name": data.get("report_name", ""),
        "phone":       data.get("phone", ""),
        "user_email":  data.get("user_email", ""),
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


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=False)