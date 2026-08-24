plugins {
  java
  id("io.gatling.gradle")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

gatling {
  jvmArgs = listOf(
    "-Xms512m",
    "-Xmx2G",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
  )
}

dependencies {
  gatling("io.gatling.highcharts:gatling-charts-highcharts:3.13.5")
}
