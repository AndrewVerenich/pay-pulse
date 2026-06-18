FROM gradle:8.10.2-jdk21 AS build
WORKDIR /home/gradle/project
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY projection-balance ./projection-balance
RUN gradle :projection-balance:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /home/gradle/project/projection-balance/build/libs/projection-balance.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
