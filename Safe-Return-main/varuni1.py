from flask import Flask, request, jsonify
from flask_cors import CORS
import random, time, smtplib
from email.mime.text import MIMEText

app = Flask(__name__)
CORS(app)

# ── In-memory stores ─────────────────────────────────────────────
otp_store  = {}   # { contact: { otp, expires_at } }
user_store  = {}  # { email/phone: { name, contact, password } }
admin_store = {}  # { email: { ... } }
org_store   = {}  # { govtReg: { ... } }

# ── Email config ─────────────────────────────────────────────────
EMAIL_ADDRESS = "vj8702889@gmail.com"
EMAIL_PASSWORD = "ablrzdsjoshjemig"

def send_email(to, otp):
    msg = MIMEText(f"Your verification code is: {otp}\n\nValid for 5 minutes.")
    msg["Subject"] = "Your Verification Code"
    msg["From"] = EMAIL_ADDRESS
    msg["To"] = to
    with smtplib.SMTP("smtp.gmail.com", 587) as server:
        server.starttls()
        server.login(EMAIL_ADDRESS, EMAIL_PASSWORD)
        server.send_message(msg)

def format_phone(phone):
    c = phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
    if c.startswith("+"): return c
    if c.startswith("91") and len(c) == 12: return "+" + c
    if len(c) == 10: return "+91" + c
    return "+" + c

# ── POST /send-otp ───────────────────────────────────────────────
@app.route("/send-otp", methods=["POST"])
def send_otp():
    data  = request.json
    email = data.get("email")
    phone = data.get("phone")
    otp   = str(random.randint(100000, 999999))
    exp   = time.time() + 300

    if email:
        try:
            otp_store[email.lower()] = {"otp": otp, "expires_at": exp}
            send_email(email, otp)
            print(f"[OTP] Sent {otp} to {email}")
            return jsonify({"message": "OTP sent successfully."})
        except Exception as e:
            print(f"[OTP ERROR] {e}")
            return jsonify({"message": f"Failed to send OTP: {str(e)}"}), 500

    elif phone:
        p = format_phone(phone)
        otp_store[p] = {"otp": otp, "expires_at": exp}
        print(f"[OTP] Phone OTP for {p} is: {otp}")
        return jsonify({"message": "OTP sent successfully."})

    return jsonify({"message": "Phone or email is required."}), 400

# ── POST /verify-otp ─────────────────────────────────────────────
@app.route("/verify-otp", methods=["POST"])
def verify_otp():
    data    = request.json
    email   = data.get("email")
    phone   = data.get("phone")
    otp     = data.get("otp")

    if not otp:
        return jsonify({"message": "OTP is required."}), 400

    contact = email.lower() if email else (format_phone(phone) if phone else None)
    if not contact:
        return jsonify({"message": "Phone or email is required."}), 400

    record = otp_store.get(contact)
    if not record:
        return jsonify({"message": "OTP not found. Request a new one."}), 400
    if time.time() > record["expires_at"]:
        otp_store.pop(contact, None)
        return jsonify({"message": "OTP expired. Request a new one."}), 400
    if record["otp"] != otp:
        return jsonify({"message": "Invalid OTP."}), 400

    otp_store.pop(contact, None)
    return jsonify({"message": "OTP verified successfully.", "contact": contact})

# ── POST /signup/user ─────────────────────────────────────────────
@app.route("/signup/user", methods=["POST"])
def signup_user():
    data     = request.json
    name     = data.get("name", "").strip()
    contact  = data.get("contact", "").strip()
    password = data.get("password", "")

    if not name or not contact or not password:
        return jsonify({"message": "Name, contact, and password are required."}), 400
    if len(password) < 6:
        return jsonify({"message": "Password must be at least 6 characters."}), 400

    key = contact.lower() if "@" in contact else format_phone(contact)
    if key in user_store:
        return jsonify({"message": "An account with this contact already exists."}), 409

    user_store[key] = {"name": name, "contact": key, "password": password, "role": "user"}
    print(f"[Signup] New user: {name} ({key})")
    return jsonify({"message": "User account created successfully!", "name": name, "contact": key})

# ── POST /login/user ──────────────────────────────────────────────
@app.route("/login/user", methods=["POST"])
def login_user():
    data     = request.json
    contact  = data.get("contact", "").strip()
    password = data.get("password", "")

    if not contact or not password:
        return jsonify({"message": "Contact and password are required."}), 400

    key  = contact.lower() if "@" in contact else format_phone(contact)
    user = user_store.get(key)

    if not user:
        return jsonify({"message": "No account found with this contact."}), 404
    if user["password"] != password:
        return jsonify({"message": "Incorrect password."}), 401

    return jsonify({"message": "Login successful!", "name": user["name"], "role": "user"})

# ── POST /signup/admin ────────────────────────────────────────────
@app.route("/signup/admin", methods=["POST"])
def signup_admin():
    data        = request.json
    admin_name  = data.get("adminName", "").strip()
    admin_email = data.get("adminEmail", "").strip().lower()
    org_reg     = data.get("orgRegistrationNumber", "").strip()
    org_pw      = data.get("orgPassword", "")
    personal_pw = data.get("personalPassword", "")

    if not all([admin_name, admin_email, org_reg, org_pw, personal_pw]):
        return jsonify({"message": "All fields are required."}), 400
    if len(personal_pw) < 6:
        return jsonify({"message": "Personal password must be at least 6 characters."}), 400

    org = next((o for o in org_store.values() if o["govtReg"] == org_reg.upper()), None)
    if not org:
        return jsonify({"message": "Organisation registration number not found."}), 404
    if org["password"] != org_pw:
        return jsonify({"message": "Incorrect organisation password."}), 401
    if admin_email in admin_store:
        return jsonify({"message": "An admin account with this email already exists."}), 409

    admin_store[admin_email] = {
        "name": admin_name, "email": admin_email,
        "orgReg": org_reg, "personalPassword": personal_pw,
        "orgName": org["name"], "role": "admin"
    }
    return jsonify({"message": "Admin account created successfully!", "name": admin_name})

# ── POST /login/admin ─────────────────────────────────────────────
@app.route("/login/admin", methods=["POST"])
def login_admin():
    data     = request.json
    email    = data.get("email", "").strip().lower()
    password = data.get("password", "")

    if not email or not password:
        return jsonify({"message": "Email and password are required."}), 400

    admin = admin_store.get(email)
    if not admin:
        return jsonify({"message": "No admin account found with this email."}), 404
    if admin["personalPassword"] != password:
        return jsonify({"message": "Incorrect password."}), 401

    return jsonify({"message": "Admin login successful!", "name": admin["name"], "org": admin["orgName"]})

# ── POST /signup/organisation ─────────────────────────────────────
@app.route("/signup/organisation", methods=["POST"])
def signup_org():
    data     = request.json
    org_name = data.get("orgName", "").strip()
    org_addr = data.get("orgAddress", "").strip()
    govt_reg = data.get("govtRegistrationNumber", "").strip().upper()
    password = data.get("password", "")
    confirm  = data.get("confirmPassword", "")

    if not all([org_name, org_addr, govt_reg, password, confirm]):
        return jsonify({"message": "All fields are required."}), 400
    if password != confirm:
        return jsonify({"message": "Passwords do not match."}), 400
    if len(password) < 6:
        return jsonify({"message": "Password must be at least 6 characters."}), 400
    if govt_reg in org_store:
        return jsonify({"message": "Organisation already exists."}), 409

    org_store[govt_reg] = {
        "name": org_name, "address": org_addr,
        "govtReg": govt_reg, "password": password, "role": "organisation"
    }
    return jsonify({"message": "Organisation registered successfully!", "name": org_name})

# ── Run ───────────────────────────────────────────────────────────
if __name__ == "__main__":
    import os
    # Use stat-based reloader scoped to this file only.
    # Prevents watchdog from endlessly scanning TensorFlow / cv2 site-packages.
    app.run(
        port=8080,
        debug=True,
        use_reloader=True,
        reloader_type='stat',
        extra_files=[os.path.abspath(__file__)]
    )