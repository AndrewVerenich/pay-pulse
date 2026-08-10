plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("io.spring.dependency-management")
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
  }
}

dependencies {
  api(project(":shared:saga-model"))

  api("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("org.springframework.kafka:spring-kafka")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("io.projectreactor:reactor-core")
  implementation("org.slf4j:slf4j-api")
}
