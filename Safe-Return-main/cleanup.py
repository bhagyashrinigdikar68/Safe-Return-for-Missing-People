from pymongo import MongoClient
import os

client = MongoClient("mongodb+srv://render_user:Vanshika0509@cluster0.6ds8ydm.mongodb.net/missing_person_db")
collection = client["missing_person_db"]["user_login_details"]

UPLOAD_FOLDER = r"C:\Users\bhagy\Downloads\Safe-Return-for-Missing-People\Safe-Return-main\uploads\photos"

for doc in collection.find({"photo_path": {"$exists": True}}):
    path = doc.get("photo_path", "")
    if path:
        filename = path.split("/")[-1]
        full_path = os.path.join(UPLOAD_FOLDER, filename)
        if not os.path.exists(full_path):
            print(f"Deleting: {doc.get('full_name')} --> {filename}")
            collection.delete_one({"_id": doc["_id"]})

print("Done! Cleanup complete.")