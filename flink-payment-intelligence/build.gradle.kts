import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  id("com.github.johnrengelman.shadow") version "8.1.1"
}

// Flink 1.17.x таргетится на JVM 11. Корневой subprojects-блок JVM 21 этот модуль пропускает (см. корневой build.gradle.kts).
java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_11)
    freeCompilerArgs.addAll("-Xjsr305=strict")
  }
}

val flinkVersion = "1.17.2"
val flinkKafkaConnectorVersion = "3.1.0-1.17"

dependencies {
  // Предоставляется Flink-кластером — не бандлим в shadowJar.
  compileOnly("org.apache.flink:flink-streaming-java:$flinkVersion")
  compileOnly("org.apache.flink:flink-clients:$flinkVersion")

  implementation("org.apache.flink:flink-connector-kafka:$flinkKafkaConnectorVersion")
  implementation("org.apache.flink:flink-connector-base:$flinkVersion")
  implementation("org.apache.flink:flink-metrics-prometheus:$flinkVersion")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
  implementation(kotlin("stdlib"))

  testImplementation("org.apache.flink:flink-streaming-java:$flinkVersion")
  testImplementation("org.apache.flink:flink-clients:$flinkVersion")
  testImplementation("org.apache.flink:flink-test-utils:$flinkVersion")
  testImplementation(platform("org.junit:junit-bom:5.10.2"))
  testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.shadowJar {
  archiveClassifier.set("all")
  archiveVersion.set("")
  mergeServiceFiles()
  manifest {
    attributes["Main-Class"] = "com.paypulse.flink.PaymentIntelligenceJob"
  }
}

tasks.test {
  // Flink 1.17 на JDK 17+ требует доступ к внутренним модулям JDK (Kryo/akka).
  jvmArgs(
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.action=ALL-UNNAMED",
  )
}

tasks.named("build") {
  dependsOn("shadowJar")
}
