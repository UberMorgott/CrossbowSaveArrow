plugins {
    id("java-library")
}

group = "morgott"
version = "0.0.2"

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    // FabricMC maven for Mixin
    maven("https://maven.fabricmc.net/")
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))

    // Mixin (provided by Hyxin at runtime)
    compileOnly("net.fabricmc:sponge-mixin:0.15.4+mixin.0.8.7")

    // MixinExtras (also provided by Hyxin)
    compileOnly("io.github.llamalad7:mixinextras-common:0.4.1")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = 21
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }

    jar {
        archiveBaseName.set("CrossbowSaveArrow")
        archiveVersion.set("0.0.2")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
