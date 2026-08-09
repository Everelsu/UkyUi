plugins {
    id("java-library")
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.2"
}

// ---- Project coordinates ----
//
// Keep `version` and UkyUI.VERSION together. Forge reads the version from the @Mod
// annotation, which needs a compile-time constant and so cannot be given this one;
// mcmod.info gets it from here through the processResources filter below. If the two
// disagree, the mod list and the file name disagree, and it is the mod list people
// quote in bug reports.
group = "com.console.uky"
version = "0.5.0"

// Java 8 toolchain is mandatory for 1.7.10 (both compiling and running)
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

// ---- RetroFuturaGradle / Minecraft configuration ----
minecraft {
    mcVersion.set("1.7.10")

    // Name shown for the dev-environment player
    username.set("Developer")

    // If you later add Mixin support for stuff like the loading screen ASM hook:
    // extraTweakClasses.add("org.spongepowered.asm.launch.MixinTweaker")
}

// Fill mcmod.info's ${modVersion} placeholder with the Gradle project version
tasks.processResources.configure {
    val projVersion = project.version.toString()
    inputs.property("version", projVersion)
    filesMatching("mcmod.info") {
        expand(mapOf("modVersion" to projVersion))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "GTNH Maven"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
    }
}

dependencies {
    // UniMixins bundles SpongePowered Mixin + MixinBooterLegacy for 1.7.10.
    // The ":dev" (deobfuscated) classifier is used for compiling and the dev run.
    // In production the pack must also ship UniMixins as a mod.
    implementation("io.github.legacymoddingmc:unimixins:0.3.1:dev")

    // Add deobfuscated third-party mod jars here later if needed, e.g.:
    // implementation(rfg.deobf(project.files("libs/somejar.jar")))
}

// Register our coremod (which provides the mixin config) for the dev run.
listOf(tasks.named<JavaExec>("runClient"), tasks.named<JavaExec>("runServer")).forEach { t ->
    t.configure {
        systemProperty("fml.coreMods.load", "com.console.uky.core.UkyCore")
    }
}

// The dev client opens at 854x480 by default, which is too small to judge a menu
// built around a full-width backdrop.
tasks.named<JavaExec>("runClient").configure {
    args("--width", "1280", "--height", "720")
}

// Coremod / mixin metadata for the built (production) jar.
tasks.named<Jar>("jar").configure {
    manifest {
        attributes(
            "FMLCorePlugin" to "com.console.uky.core.UkyCore",
            "FMLCorePluginContainsFMLMod" to "true",
            "ForceLoadAsMod" to "true",
            "MixinConfigs" to "mixins.uky.early.client.json"
        )
    }
}
