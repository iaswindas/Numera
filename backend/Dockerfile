FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
