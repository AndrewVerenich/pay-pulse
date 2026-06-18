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
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("org.springframework:spring-tx")
  implementation("org.springframework.data:spring-data-r2dbc")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
}
