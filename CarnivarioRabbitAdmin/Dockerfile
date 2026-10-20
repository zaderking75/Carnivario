FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /app/target/CarnivarioRabbitAdmin-0.0.1-SNAPSHOT.jar ./app.jar
ENV SERVER_ADDRESS=0.0.0.0
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
