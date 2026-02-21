FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Default to H2 profile (no external DB required)
# Override with: -e SPRING_PROFILES_ACTIVE=postgres
ENV SPRING_PROFILES_ACTIVE=h2

# H2 file database lives at /app/data — mount a volume here for persistence
RUN mkdir -p /app/data
VOLUME ["/app/data"]

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
