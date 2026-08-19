# build SPA
FROM node:20-alpine AS ui-build
WORKDIR /app
COPY ops-dashboard-ui/package.json ops-dashboard-ui/package-lock.json* ./
RUN npm install
COPY ops-dashboard-ui/ ./
RUN npm run build

# build Netty static+proxy server (Java/Netty host-port publish works under snap-docker; nginx does not)
FROM gradle:8.10.2-jdk21 AS server-build
WORKDIR /home/gradle/project
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY ops-ui-server ./ops-ui-server
COPY --from=ui-build /app/dist/ ./ops-ui-server/src/main/resources/static/
RUN gradle :ops-ui-server:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=server-build /home/gradle/project/ops-ui-server/build/libs/ops-ui-server.jar /app/app.jar
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
