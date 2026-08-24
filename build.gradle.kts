import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") apply false
  kotlin("plugin.spring") apply false
  id("org.springframework.boot") apply false
  id("io.spring.dependency-management") apply false
}

allprojects {
  group = "com.paypulse"
  version = "0.1.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }
}

subprojects {
  plugins.withId("org.jetbrains.kotlin.jvm") {
    // flink-payment-intelligence ограничен JVM 11 (Flink 1.17.x) и конфигурируется в своём build-скрипте.
    if (name != "flink-payment-intelligence") {
      extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
      }
      tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
          jvmTarget.set(JvmTarget.JVM_21)
          freeCompilerArgs.addAll("-Xjsr305=strict", "-Xjvm-default=all")
        }
      }
    }
    tasks.withType<Test>().configureEach {
      useJUnitPlatform()
    }
  }
}
