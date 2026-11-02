// Pure-Kotlin detection engine. No Android dependencies on purpose:
//  - unit tests run on the JVM in milliseconds
//  - the same artifact can be sold as the "ScamShield SDK" to banks/fintech apps
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

tasks.withType<Test> {
    useJUnit()
}
