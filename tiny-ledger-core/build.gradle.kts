plugins {
    id("buildsrc.convention.kotlin-jvm")
    kotlin("jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.arrow-kt:arrow-core:2.1.1")
    implementation(libs.kotlinxSerialization)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}