import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
plugins {
    kotlin("jvm") version "1.3.72"
    id("kr.entree.spigradle") version "1.2.4"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

group = "me.jantuck"
version = "1.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven ("https://jitpack.io")
    maven(url = "https://nexus.okkero.com/repository/maven-releases/")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.okkero", "Skedule", "v1.2.6")
    implementation("com.esotericsoftware", "reflectasm", "1.11.9")
    implementation("org.slf4j", "slf4j-api",  "1.7.30")
    implementation("org.slf4j", "slf4j-simple",  "1.7.30")
    implementation("org.reflections", "reflections", "0.9.12")
    compileOnly("org.spigotmc:spigot-api:1.16.1-R0.1-SNAPSHOT")
}
spigot {
    authors = listOf("JanTuck")
    apiVersion = "1.13"
}
val autoRelocate by tasks.register<ConfigureShadowRelocation>("configureShadowRelocation", ConfigureShadowRelocation::class) {
    target = tasks.getByName("shadowJar") as ShadowJar?
    val packageName = "${project.group}.${project.name.toLowerCase()}"
    prefix = "$packageName.shaded"
}

tasks {
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    withType<ShadowJar> {
        archiveClassifier.set("")
        dependsOn(autoRelocate)
        minimize()
    }
}