@file:Suppress("Unused", "SpellCheckingInspection")

plugins {
    id("zenithproxy.plugin.dev") version "1.0.0-SNAPSHOT"
}

group = properties["maven_group"] as String
version = properties["plugin_version"] as String
val mc = properties["mc"] as String
val pluginId = properties["plugin_id"] as String

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

zenithProxyPlugin {
    templateProperties = mapOf(
        "version" to project.version,
        "mc_version" to mc,
        "plugin_id" to pluginId,
        "maven_group" to group as String,
    )
    javaReleaseVersion = JavaLanguageVersion.of(21)
}

repositories {
    maven("https://maven.2b2t.vc/releases") {
        name = "ZenithProxy Releases"
    }
    maven("https://maven.2b2t.vc/remote") {
        name = "ZenithProxy Dependencies"
    }
}

dependencies {
    zenithProxy("com.zenith:ZenithProxy:$mc-SNAPSHOT")
}

tasks {
    shadowJar {
        // Configure shadow jar if needed
    }
}