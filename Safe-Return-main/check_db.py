from pymongo import MongoClient
col = MongoClient('mongodb+srv://render_user:Vanshika0509@cluster0.6ds8ydm.mongodb.net/missing_person_db')['missing_person_db']['user_login_details']
for doc in col.find({}, {'full_name':1, 'contact_email':1, 'inmate_id':1, 'person_id':1}):
    print(doc)