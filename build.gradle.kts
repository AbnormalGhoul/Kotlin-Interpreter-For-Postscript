plugins {
    kotlin("jvm") version "2.2.20"
    application
}

group = "net.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

// Qualified main class, aka where the `main` function is defined
application {
    mainClass.set("postscript.MainKt")
}

// Use JUnit platform for tests
tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}