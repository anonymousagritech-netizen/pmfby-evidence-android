// Pure-JVM Kotlin module: deliberately has ZERO Android/AndroidX imports, mirroring the
// same strategy the earlier React Native prototype used (see ../README.md and the
// repo-split note in ../../backend-and-web/IMPLEMENTATION_PLAN.md) — business logic that
// must be unit-testable in a sandbox with no device/emulator lives here, compiled and
// tested as plain JVM Kotlin with plain JUnit, no Android SDK/Gradle plugin required at
// all for `:core:test`.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
