plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("org.springframework.boot")
  id("io.spring.dependency-management")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

dependencies {
  implementation(project(":shared:metrics-starter"))

  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("org.postgresql:r2dbc-postgresql")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.networknt:json-schema-validator:1.5.1")
  implementation("io.micrometer:micrometer-registry-prometheus")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

tasks.bootJar {
  archiveFileName.set("rule-management-service.jar")
}
