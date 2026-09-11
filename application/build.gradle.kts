plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.google.cloud.tools.jib") version "3.5.4"
}

group = "com.akl"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val mockitoAgent = configurations.create("mockitoAgent")

repositories {
    mavenCentral()
}

dependencies {
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
    implementation(project(":domain"))
    implementation(project(":storage"))
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("io.opentelemetry.proto:opentelemetry-proto:1.8.0-alpha")
    implementation("com.google.protobuf:protobuf-java")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.28.0-alpha")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.31.1")
    implementation("net.ttddyy.observation:datasource-micrometer-spring-boot:2.2.1")

    val kotlinxHtmlVersion = "0.12.0"
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:${kotlinxHtmlVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-html:${kotlinxHtmlVersion}")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

// Read once at configuration time so docker-compose.yml and Testcontainers both
// start the exact same Postgres image, declared in a single place.
val postgresVersion = rootProject.file(".env").readLines()
    .first { it.startsWith("POSTGRES_VERSION=") }
    .substringAfter("=")
    .trim()

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    systemProperty("postgres.version", postgresVersion)
}


jib {
    from {
        image = "eclipse-temurin:25-jre-alpine"
    }
    to {
        image = "ghcr.io/4kleiber/kotlin-bc-template"
    }
    container {
        ports = listOf("8080")
    }
}
