import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*

plugins {
	kotlin("jvm")
	kotlin("plugin.serialization")
	id("io.github.detekt.gradle.compiler-plugin")
    id("java-library")
    id("maven-publish")
    id("org.jetbrains.dokka")
}

kotlin {
    explicitApi()
}

detekt {
    config.setFrom("detekt.yml")
}

dependencies {
	implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
    detektPlugins(project(":detekt-treapability"))
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
	testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
}
