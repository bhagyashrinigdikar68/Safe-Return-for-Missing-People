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

UPLOAD_FOLDER = "uploads/photos"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

@app.route("/submit", methods=["POST"])
def submit():
    data = request.form
    photo = request.files.get("photo")

    phone_number = data.get("public-familyPhone", "").strip()

    if not re.match(r'^[6-9]\d{9}$', phone_number):
        return jsonify({
            "error": "Invalid Indian phone number (must be 10 digits and start with 6-9)"
        }), 400
    
    last_seen = data.get("public-dateTime")

    if not last_seen:
        return jsonify({
            "error": "Last seen date & time is required."
        }), 400

    try:
        selected_date = datetime.fromisoformat(last_seen)
        current_date = datetime.now()

        if selected_date > current_date:
            return jsonify({
                "error": "Future date is not allowed."
            }), 400
    except ValueError:
        return jsonify({
            "error": "Invalid date format."
        }), 400

    photo_path = None

    if photo:
        filename = secure_filename(photo.filename)
        photo_path = os.path.join(UPLOAD_FOLDER, filename)
        photo.save(photo_path)

    document = {
        "full_name": data.get("public-fullName"),
        "age": data.get("public-age"),
        "gender": data.get("gender"),
        "language_spoken": data.get("language_spoken"),
        "last_seen_location": data.get("public-location"),
        "last_seen_datetime": data.get("public-dateTime"),
        "clothing_description": data.get("clothing_description"),
        "general_description": data.get("general_description"),
        "medical_condition": data.get("medical_condition"),
        "contact_name": data.get("public-familyName"),
        "contact_phone": phone_number,
        "photo_path": photo_path,
        "status": "Missing"
    }

    collection.insert_one(document)

    return jsonify({"message": "Report submitted successfully"})

@app.route("/uploads/photos/<filename>")
def get_photo(filename):
    return send_from_directory("uploads/photos", filename)

@app.route("/get-missing-reports", methods=["GET"])
def get_missing_reports():
    reports = list(collection.find())

    # Convert ObjectId to string
    for r in reports:
        r["_id"] = str(r["_id"])

    return jsonify(reports)

@app.route("/get-reports", methods=["GET"])
def get_reports():
    reports = list(collection.find())

    for r in reports:
        r["_id"] = str(r["_id"])
        r["status"] = r.get("status", "Missing")  # default status

    return jsonify(reports)

@app.route("/mark-found/<report_id>", methods=["POST"])
def mark_found(report_id):
    collection.update_one(
        {"_id": ObjectId(report_id)},
        {"$set": {"status": "Found"}}
    )

    return jsonify({"message": "Marked as Found"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)