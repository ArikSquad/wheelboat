plugins {
    id("net.fabricmc.fabric-loom") version "1.16.3"
    id("maven-publish")
}

version = "1.0.0"
group = "eu.mikart.wheelboat"
val modVersion = version.toString()

base {
    archivesName = "wheelboat"
}

repositories {
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:26.1.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("me.shedaniel.cloth:cloth-config-fabric:26.1.154") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    implementation("com.terraformersmc:modmenu:18.0.0-alpha.8")
}

tasks.processResources {
    inputs.property("version", modVersion)

    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
