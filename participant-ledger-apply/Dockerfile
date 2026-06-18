FROM gradle:8.10.2-jdk21 AS build
WORKDIR /home/gradle/project
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY shared ./shared
COPY participant-ledger-apply ./participant-ledger-apply
RUN gradle :participant-ledger-apply:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /home/gradle/project/participant-ledger-apply/build/libs/participant-ledger-apply.jar /app/app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
