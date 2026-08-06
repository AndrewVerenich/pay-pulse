FROM gradle:8.10.2-jdk21 AS build
WORKDIR /home/gradle/project
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY rule-management-service ./rule-management-service
RUN gradle :rule-management-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /home/gradle/project/rule-management-service/build/libs/rule-management-service.jar /app/app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
