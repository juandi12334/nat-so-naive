# ---------- build stage: compile and package ----------
# The JUnit suite (mvn test) is run before every push; the image build skips it to keep
# the free build environment fast and within its memory limits.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/not-so-naive.jar /app/app.jar
# Project sources, served as a ZIP at /entrega (target/ and .git are excluded by .dockerignore)
COPY . /app/source
ENV PROJECT_ROOT=/app/source \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
