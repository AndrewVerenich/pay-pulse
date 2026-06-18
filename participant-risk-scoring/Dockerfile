FROM gradle:8.10.2-jdk21 AS build
WORKDIR /home/gradle/project
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY participant-risk-scoring ./participant-risk-scoring
RUN gradle :participant-risk-scoring:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /home/gradle/project/participant-risk-scoring/build/libs/participant-risk-scoring.jar /app/app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
