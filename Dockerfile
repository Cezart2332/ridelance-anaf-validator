# --- build ---
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# --- runtime ---
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /validators /tmp/anaf-validator \
    && chown app:app /tmp/anaf-validator
WORKDIR /app
COPY --from=build /src/target/anaf-validator.jar app.jar

# Kitul ANAF nu e în imagine: se montează ca volum în /validators.
ENV VALIDATORS_PATH=/validators \
    WORKSPACE_PATH=/tmp/anaf-validator

USER app
# Portul vine din PORT (implicit 8080). Healthcheck-ul îl urmează: cu PORT=9020 și un healthcheck fix
# pe 8080, containerul devenea „unhealthy”, iar proxy-ul Coolify răspundea 503 la orice validare.
ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS "http://localhost:${PORT}/actuator/health" || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=40", "-jar", "/app/app.jar"]
