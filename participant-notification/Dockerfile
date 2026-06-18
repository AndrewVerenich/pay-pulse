FROM gradle:8.10.2-jdk21 AS build
WORKDIR /home/gradle/project
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY participant-notification ./participant-notification
RUN gradle :participant-notification:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /home/gradle/project/participant-notification/build/libs/participant-notification.jar /app/app.jar
EXPOSE 8094
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
