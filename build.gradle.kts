import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.4.1"
}

group = "me.jantuck"
version = "2.0-SNAPSHOT"

val pluginVersion = version

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.esotericsoftware:reflectasm:1.11.9")
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("org.slf4j:slf4j-nop:1.7.30")
    implementation("org.reflections:reflections:0.9.12")
    compileOnly("org.spigotmc:spigot-api:1.16.1-R0.1-SNAPSHOT")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            expand(mapOf("version" to pluginVersion))
        }
    }
    withType<ShadowJar> {
        archiveClassifier.set("")
        enableAutoRelocation.set(true)
        relocationPrefix.set("${project.group}.${project.name.lowercase()}.shaded")
        minimize()
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}
