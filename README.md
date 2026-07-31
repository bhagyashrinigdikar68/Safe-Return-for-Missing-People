# 🛡️ Safe Return – AI-Powered Missing People Detection System

## 📌 Overview

**Safe Return** is an AI-powered web application designed to assist in locating missing persons using facial recognition technology. The platform enables users to upload photographs of missing individuals and automatically compares them against a database of detected faces.

When a potential match is found, the system instantly notifies the concerned authorities or guardians through multiple communication channels, helping reduce response time and improving the chances of safely locating missing individuals.

---

## 🎯 Problem Statement

Thousands of missing person cases remain unresolved due to the lack of automated identification systems.

Safe Return addresses this challenge by combining:

- Artificial Intelligence
- Face Recognition
- Image Processing
- Automated Notifications
- Web Technologies

to build a fast, reliable, and scalable missing-person detection platform.

---

# ✨ Features

### 🔍 AI Face Recognition

- Detects faces from uploaded images
- Extracts facial embeddings
- Matches against the missing-person database
- Returns similarity scores

---

### 👤 Missing Person Registration

Users can:

- Upload missing person's photograph
- Enter personal details
- Store records securely

---

### 📸 Image Matching

The application compares:

- Uploaded images
- Existing database
- Newly detected faces

using deep-learning-based facial recognition.

---

### 🔔 Multi-Channel Notification System

Automatically sends alerts through:

- 📧 Email
- 📱 SMS

when a possible match is detected.

---

### 🌐 Responsive Web Interface

Simple and user-friendly interface built using:

- HTML
- CSS
- JavaScript

---

# 🏗️ System Architecture

```
User
   │
   ▼
Web Application
   │
   ▼
Python Face Recognition API
   │
   ▼
Face Detection & Matching
   │
   ▼
Notification Service
   │
   ├── Email
   ├── SMS
```

---

# 🛠️ Tech Stack

## Frontend

- HTML5
- CSS3
- JavaScript

## Backend

- Python
- Flask

## AI / Machine Learning

- OpenCV
- Face Recognition
- ArcFace
- RetinaFace
- NumPy

## Notification Service

- Java
- Spring Boot
- Maven

---

# 📂 Project Structure

```
Safe-Return-for-Missing-People/

│
├── Safe-Return-main/
│   ├── api.py
│   ├── app.py
│   ├── app_backend.py
│   ├── chatbot.js
│   ├── connect.js
│   ├── script.js
│   ├── style.css
│
├── safe-return-notifications/
│   ├── Spring Boot Notification Service
│   ├── Email Notifications
│   ├── SMS Notifications
│
└── README.md
```

---

# ⚙️ Installation

## Clone Repository

```bash
git clone https://github.com/bhagyashrinigdikar68/Safe-Return-for-Missing-People.git
```

---

## Navigate

```bash
cd Safe-Return-for-Missing-People
```

---

## Install Python Dependencies

```bash
pip install -r requirements.txt
```

---

## Run Application

```bash
python app_login.py
```
```bash
python app.py
```
```bash
python app1.py
```
```bash
python app3.py
```

---

## Notification Service

Navigate to:

```bash
safe-return-notifications
```

Run:

```bash
mvn spring-boot:run
```

---

# 📸 Workflow

1. Register missing person's information
2. Upload photograph
3. AI extracts facial embeddings
4. System compares with stored database
5. Calculates similarity score
6. If match exceeds threshold:
   - Email sent
   - SMS sent
   - Push notification sent
   - WhatsApp alert generated
7. User receives detection result

---

# 🚀 Future Improvements

- Mobile Application
- Live CCTV Integration
- Real-time Video Recognition
- Police Database Integration
- Cloud Deployment
- GPS Location Tracking
- Admin Dashboard
- Multi-language Support

---

# 📄 License

This project is licensed under the MIT License.

---

