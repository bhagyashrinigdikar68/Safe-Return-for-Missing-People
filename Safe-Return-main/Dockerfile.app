# ── Dockerfile.app  (Flask main backend – port 5001) ──
FROM python:3.10-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN mkdir -p uploads/photos

EXPOSE 5001
CMD ["python", "app.py"]
