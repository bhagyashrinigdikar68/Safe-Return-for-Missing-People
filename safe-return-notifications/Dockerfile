# ── Dockerfile  (Spring Boot Notification Service – port 8080) ──
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY notification-service-1.0.0.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]