FROM gradle:8.10.2-jdk21 AS build
WORKDIR /home/gradle/project
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY payment-generator ./payment-generator
RUN gradle :payment-generator:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /home/gradle/project/payment-generator/build/libs/payment-generator.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
