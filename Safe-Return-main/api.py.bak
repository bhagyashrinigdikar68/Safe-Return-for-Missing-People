"""
api.py  –  Flask backend for Safe Return Face Recognition
Matches all features in streamlit_app.py:
  - POST /recognize      → Face recognition against database (with GA+PSO threshold)
  - POST /compare        → Two-image face comparison (with GA+PSO threshold)
  - GET  /threshold      → Returns the current auto-computed threshold
  - GET  /health         → Health check

Run (dev):   python api.py
Run (prod):  gunicorn -w 2 -b 0.0.0.0:5000 api:app
"""

from notification_client import notify_if_match
from pymongo import MongoClient as _MongoClient

import os
import io
import base64
import uuid
import logging

import numpy as np
import pandas as pd
from PIL import Image
from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
from deepface import DeepFace

# ── Import your GA/PSO threshold optimisers ─────────────────────────────────
from threshold_optimizer import genetic_algorithm, particle_swarm_optimization

# ── Logging ──────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s – %(message)s",
)
logger = logging.getLogger("safe_return")

# ── MongoDB connection (for fetching report contact details) ──────────────────
_mongo_col = _MongoClient(
    "mongodb+srv://render_user:Vanshika0509@cluster0.6ds8ydm.mongodb.net/missing_person_db"
)["missing_person_db"]["user_login_details"]

# ── App setup ─────────────────────────────────────────────────────────────────
app = Flask(__name__, static_folder=os.path.dirname(os.path.abspath(__file__)), static_url_path="")
CORS(app)

# ── Config (override via environment variables) ───────────────────────────────
BASE_DIR        = os.path.dirname(os.path.abspath(__file__))
EXCEL_PATH      = os.environ.get("EXCEL_PATH", os.path.join(BASE_DIR, "sheet.xlsx"))
DB_PATH         = os.environ.get("DB_PATH",    os.path.join(BASE_DIR, "data"))
TEMP_DIR        = os.environ.get("TEMP_DIR",   os.path.join(BASE_DIR, "temp_uploads"))
THRESHOLD_CACHE = os.path.join(BASE_DIR, "threshold.npy")

os.makedirs(TEMP_DIR, exist_ok=True)

# ════════════════════════════════════════════════════════════════════════════════
#  GA + PSO Auto Threshold
# ════════════════════════════════════════════════════════════════════════════════
_threshold_cache: float | None = None


def compute_optimal_threshold() -> float:
    global _threshold_cache
    if _threshold_cache is not None:
        return _threshold_cache

    if os.path.exists(THRESHOLD_CACHE):
        _threshold_cache = float(np.load(THRESHOLD_CACHE))
        logger.info("Threshold loaded from cache: %.4f", _threshold_cache)
        return _threshold_cache

    genuine_path  = os.path.join(BASE_DIR, "genuine_distances.npy")
    imposter_path = os.path.join(BASE_DIR, "imposter_distances.npy")

    if not os.path.exists(genuine_path) or not os.path.exists(imposter_path):
        logger.warning("Distance files not found – using fallback threshold 0.6")
        _threshold_cache = 0.6
        return _threshold_cache

    try:
        genuine  = np.load(genuine_path)
        imposter = np.load(imposter_path)

        ga_threshold  = genetic_algorithm(genuine, imposter)
        pso_threshold = particle_swarm_optimization(genuine, imposter)

        _threshold_cache = float((ga_threshold + pso_threshold) / 2)
        np.save(THRESHOLD_CACHE, _threshold_cache)
        logger.info("Threshold computed & saved: %.4f", _threshold_cache)
    except Exception as exc:
        logger.error("Threshold computation failed: %s – using 0.6", exc)
        _threshold_cache = 0.6

    return _threshold_cache


# ════════════════════════════════════════════════════════════════════════════════
#  Excel helper  (lazy-loaded, cached in memory)
# ════════════════════════════════════════════════════════════════════════════════
_person_df: pd.DataFrame | None = None


def get_person_df() -> pd.DataFrame:
    global _person_df
    if _person_df is None:
        _person_df = pd.read_excel(EXCEL_PATH)
        logger.info("Excel loaded: %d rows", len(_person_df))
    return _person_df


# ════════════════════════════════════════════════════════════════════════════════
#  Image decode helper
# ════════════════════════════════════════════════════════════════════════════════
def decode_image_to_file(b64_data: str, suffix: str = ".jpg") -> str:
    if "," in b64_data:
        b64_data = b64_data.split(",", 1)[1]
    b64_data = b64_data.strip().replace(" ", "+").replace("\n", "").replace("\r", "")
    missing = len(b64_data) % 4
    if missing:
        b64_data += "=" * (4 - missing)
    img_bytes = base64.b64decode(b64_data)
    img = Image.open(io.BytesIO(img_bytes)).convert("RGB")
    temp_path = os.path.join(TEMP_DIR, f"{uuid.uuid4().hex}{suffix}")
    img.save(temp_path, "JPEG")
    return temp_path


def cleanup(*paths):
    for p in paths:
        try:
            if p and os.path.exists(p):
                os.remove(p)
        except OSError:
            pass


# ════════════════════════════════════════════════════════════════════════════════
#  ROUTE: GET /
# ════════════════════════════════════════════════════════════════════════════════
@app.route("/")
def index():
    return app.send_static_file("index.html")


# ════════════════════════════════════════════════════════════════════════════════
#  ROUTE: Proxy photo requests
# ════════════════════════════════════════════════════════════════════════════════
@app.route("/uploads/photos/<path:filename>")
def proxy_photo(filename):
    photo_folder = os.path.join(os.path.dirname(os.path.abspath(__file__)), "uploads", "photos")
    return send_from_directory(photo_folder, filename)


# ════════════════════════════════════════════════════════════════════════════════
#  ROUTE: GET /health
# ════════════════════════════════════════════════════════════════════════════════
@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status":    "ok",
        "excel":     os.path.exists(EXCEL_PATH),
        "db":        os.path.exists(DB_PATH),
        "threshold": compute_optimal_threshold(),
    })


# ════════════════════════════════════════════════════════════════════════════════
#  ROUTE: GET /threshold
# ════════════════════════════════════════════════════════════════════════════════
@app.route("/threshold", methods=["GET"])
def get_threshold():
    threshold = compute_optimal_threshold()
    return jsonify({
        "success":   True,
        "threshold": threshold,
    })


# ════════════════════════════════════════════════════════════════════════════════
#  ROUTE: POST /recognize
# ════════════════════════════════════════════════════════════════════════════════
@app.route("/recognize", methods=["POST"])
def recognize():
    data       = request.get_json(force=True)
    image_data = data.get("image", "")
    location   = data.get("location", "Unknown Camera")

    if not image_data:
        return jsonify({"success": False, "error": "No image provided"}), 400

    temp_path = None
    try:
        temp_path = decode_image_to_file(image_data)
    except Exception as exc:
        return jsonify({"success": False, "error": f"Invalid image data: {exc}"}), 400

    try:
        threshold = compute_optimal_threshold()

        results = DeepFace.find(
            img_path=temp_path,
            db_path=DB_PATH,
            model_name="ArcFace",
            detector_backend="retinaface",
            distance_metric="cosine",
            enforce_detection=False,
            silent=True,
        )

        if not results or len(results[0]) == 0:
            return jsonify({
                "success": True,
                "match":   False,
                "message": "No matching face found in the database",
            })

        best_match   = results[0].iloc[0]
        matched_path = best_match["identity"]
        distance     = float(best_match["distance"])
        confidence   = round(1 - distance, 4)
        confidence_pct = round(confidence * 100, 2)

        person_id = os.path.splitext(os.path.basename(matched_path))[0]

        df  = get_person_df()
        row = df[df["Inmate Id"].astype(str) == str(person_id)]
        if row.empty:
            return jsonify({
                "success": False,
                "error":   f"Person ID '{person_id}' not found in Excel sheet",
            })

        person_name = str(row.iloc[0]["Name"])
        matched_above_threshold = confidence >= threshold

        matched_b64 = ""
        try:
            with open(matched_path, "rb") as f:
                matched_b64 = "data:image/jpeg;base64," + base64.b64encode(f.read()).decode()
        except Exception:
            pass

        logger.info(
            "Recognize: person_id=%s name=%s confidence=%.2f%% threshold=%.4f match=%s",
            person_id, person_name, confidence_pct, threshold, matched_above_threshold,
        )

        result = {
            "success":          True,
            "match":            matched_above_threshold,
            "person_name":      person_name,
            "person_id":        person_id,
            "confidence":       confidence_pct,
            "confidence_raw":   confidence,
            "distance":         distance,
            "threshold":        threshold,
            "matched_image":    matched_b64,
            "low_confidence":   not matched_above_threshold,
        }

        # ── Fetch reporter details from MongoDB and send notification ──────────
        report = _mongo_col.find_one({"inmate_id": person_id})

        notify_if_match(
            result,
            location            = location,
            family_email        = report.get("contact_email") if report else None,
            family_phone        = report.get("contact_phone") if report else None,
            reporter_name       = report.get("contact_name")  if report else None,
            missing_person_name = report.get("full_name")     if report else None,
        )

        return jsonify(result)

    except Exception as exc:
        logger.exception("Recognition error")
        return jsonify({"success": False, "error": str(exc)})

    finally:
        cleanup(temp_path)


# ════════════════════════════════════════════════════════════════════════════════
#  ROUTE: POST /compare
# ════════════════════════════════════════════════════════════════════════════════
@app.route("/compare", methods=["POST"])
def compare():
    data     = request.get_json(force=True)
    img1_b64 = data.get("image1", "")
    img2_b64 = data.get("image2", "")
    location = data.get("location", "Unknown Camera")

    if not img1_b64 or not img2_b64:
        return jsonify({"success": False, "error": "Two images required"}), 400

    path1 = path2 = None
    try:
        path1 = decode_image_to_file(img1_b64)
        path2 = decode_image_to_file(img2_b64)
    except Exception as exc:
        cleanup(path1, path2)
        return jsonify({"success": False, "error": f"Invalid image data: {exc}"}), 400

    try:
        threshold = compute_optimal_threshold()

        result = DeepFace.verify(
            img1_path=path1,
            img2_path=path2,
            model_name="ArcFace",
            detector_backend="retinaface",
            distance_metric="cosine",
            enforce_detection=True,
        )

        distance       = float(result["distance"])
        confidence     = round(1 - distance, 4)
        confidence_pct = round(confidence * 100, 2)
        verified       = distance <= threshold

        if verified and confidence > 0.75:
            level = "high"
        elif verified:
            level = "borderline"
        else:
            level = "no_match"

        logger.info(
            "Compare: distance=%.4f threshold=%.4f confidence=%.2f%% verified=%s",
            distance, threshold, confidence_pct, verified,
        )

        return jsonify({
            "success":            True,
            "verified":           verified,
            "confidence":         confidence_pct,
            "confidence_raw":     confidence,
            "distance":           distance,
            "threshold":          threshold,
            "deepface_threshold": float(result.get("threshold", threshold)),
            "confidence_level":   level,
        })

    except Exception as exc:
        logger.exception("Comparison error")
        return jsonify({"success": False, "error": str(exc)})

    finally:
        cleanup(path1, path2)


# ════════════════════════════════════════════════════════════════════════════════
#  ROUTE: POST /reload-excel
# ════════════════════════════════════════════════════════════════════════════════
@app.route("/reload-excel", methods=["POST"])
def reload_excel():
    global _person_df
    try:
        _person_df = pd.read_excel(EXCEL_PATH)
        logger.info("Excel reloaded: %d rows", len(_person_df))
        return jsonify({"success": True, "rows": len(_person_df)})
    except Exception as exc:
        return jsonify({"success": False, "error": str(exc)})


# ════════════════════════════════════════════════════════════════════════════════
#  ROUTE: POST /reload-threshold
# ════════════════════════════════════════════════════════════════════════════════
@app.route("/reload-threshold", methods=["POST"])
def reload_threshold():
    global _threshold_cache
    _threshold_cache = None
    if os.path.exists(THRESHOLD_CACHE):
        os.remove(THRESHOLD_CACHE)
    threshold = compute_optimal_threshold()
    return jsonify({"success": True, "threshold": threshold})


# ════════════════════════════════════════════════════════════════════════════════
#  DeepFace Warmup
# ════════════════════════════════════════════════════════════════════════════════
def warmup():
    import threading
    def _warm():
        try:
            logger.info("Warming up DeepFace (loading models)...")
            dummy = np.zeros((112, 112, 3), dtype=np.uint8)
            dummy_path = os.path.join(TEMP_DIR, "_warmup.jpg")
            Image.fromarray(dummy).save(dummy_path)
            try:
                DeepFace.represent(
                    img_path=dummy_path,
                    model_name="ArcFace",
                    detector_backend="skip",
                    enforce_detection=False,
                )
            except Exception:
                pass
            finally:
                if os.path.exists(dummy_path):
                    os.remove(dummy_path)
            logger.info("DeepFace warmup complete ✓")
        except Exception as e:
            logger.warning("Warmup failed (non-fatal): %s", e)
    threading.Thread(target=_warm, daemon=True).start()


# ════════════════════════════════════════════════════════════════════════════════
#  Entry point
# ════════════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    logger.info("Excel  : %s  %s", EXCEL_PATH, "✓" if os.path.exists(EXCEL_PATH) else "✗ NOT FOUND")
    logger.info("DB     : %s  %s", DB_PATH,    "✓" if os.path.exists(DB_PATH)    else "✗ NOT FOUND")
    warmup()
    logger.info("Threshold will be auto-computed on first request.")
    app.run(host="0.0.0.0", port=5000, debug=False, use_reloader=False)