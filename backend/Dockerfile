FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN sh gradlew bootJar --no-daemon && mv build/libs/*.jar /app.jar

FROM eclipse-temurin:21-jre
COPY --from=build /app.jar /app/app.jar
USER 65534
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
