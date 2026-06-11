# Multi-stage build. Context = backend-springboot/ (the reactor root) so the
# build can see the parent pom and the common module:
#   docker build -f auth-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
# -pl <module> -am builds this service plus its in-reactor deps (common).
# Tests already gate merges in CI; images skip them for build speed.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl auth-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/auth-service/target/auth-service-*.jar app.jar
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "app.jar"]
