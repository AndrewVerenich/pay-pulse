FROM gradle:8.10.2-jdk21 AS build
WORKDIR /home/gradle/project
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY payment-saga-orchestrator ./payment-saga-orchestrator
RUN gradle :payment-saga-orchestrator:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /home/gradle/project/payment-saga-orchestrator/build/libs/payment-saga-orchestrator.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
