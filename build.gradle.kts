import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
plugins {
    kotlin("jvm") version "1.3.72"
    id("kr.entree.spigradle") version "1.2.4"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

group = "me.jantuck"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://papermc.io/repo/repository/maven-public/")
    maven(url = "https://repo.aikar.co/content/groups/aikar/")
    maven(url = "https://nexus.okkero.com/repository/maven-releases/")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("co.aikar", "acf-paper", "0.5.0-SNAPSHOT")
    implementation("com.okkero.skedule", "skedule", "1.2.6")
    compileOnly("com.destroystokyo.paper", "paper-api", "1.16.1-R0.1-SNAPSHOT")
}
spigot {
    authors = listOf("JanTuck")
    apiVersion = "1.13"
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
        val packageName = "${project.group}.${project.name.toLowerCase()}"
        relocate("co.aikar.commands", "$packageName.shaded.acf")
        relocate("co.aikar.locales", "$packageName.shaded.locales")
        relocate( "kotlin" , "$packageName.shaded.kotlin")
        relocate( "com.okkero.skedule" , "$packageName.shaded.skedule")
        minimize()
    }
}