import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
plugins {
    kotlin("jvm") version "1.3.72"
    id("kr.entree.spigradle") version "1.2.4"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

group = "me.jantuck"
version = "1.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://papermc.io/repo/repository/maven-public/")
    maven(url = "https://nexus.okkero.com/repository/maven-releases/")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.okkero.skedule", "skedule", "1.2.6")
    implementation("com.esotericsoftware", "reflectasm", "1.11.9")
    implementation("org.reflections", "reflections", "0.9.10")
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
        relocate( "kotlin" , "$packageName.shaded.kotlin")
        relocate( "com.okkero" , "$packageName.shaded.com.okkero")
        relocate( "com.google.common" , "$packageName.shaded.google.common")
        relocate( "com.esotericsoftware" , "$packageName.shaded.com.esotericsoftware")
        relocate( "com.okkero" , "$packageName.shaded.com.okkero")
        relocate( "org.reflections" , "$packageName.shaded.org.reflections") // org.reflections, org.jetbrainsjetbrains, and org.intellij
        relocate( "org.jetbrains" , "$packageName.shaded.org.jetbrains")
        relocate( "org.intellij" , "$packageName.shaded.org.intellij")
        relocate( "net.jcip.annotations" , "$packageName.shaded.net.jcip.annotations") // net.jcip.annotations
        relocate( "javax.annotation" , "$packageName.shaded.javax.annotation") // javax.annotation
        relocate( "javassist" , "$packageName.shaded.javassist")
        relocate( "edu.umd.cs.findbugs.annotions" , "$packageName.shaded.edu.umd.cs.findbugs.annotions") // edu.umd.cs.findbugs.annotions
        minimize() // Remove unused shit
    }
}