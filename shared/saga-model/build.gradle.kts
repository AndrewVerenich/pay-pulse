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
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
