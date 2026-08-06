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

// Java 8 toolchain is mandatory for 1.12.2 (both compiling and running)
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

// ---- Stale decompile guard ----
//
// RetroFuturaGradle keeps its decompiled and patched Minecraft in build/rfg, and it
// does not clear that directory when mcVersion changes — it writes the new sources
// over whatever is already there. This repository has a 1.7.10 branch and a 1.12.2
// branch sharing one build directory, so checking out the other branch, building, and
// coming back leaves the previous version's sources sitting in the tree. The Minecraft
// recompile then fails on a hundred errors that all look like the mod's fault and none
// of which are: cpw.mods.fml, tv.twitch, WorldSettings.GameType.
//
// The tell is unambiguous — cpw/ is 1.7.10's FML and cannot exist in a 1.12.2 tree —
// so the leftovers are cleared rather than reported. Fixing it by hand is one rm and
// five minutes of decompiling, every single time.
run {
    val rfg = layout.buildDirectory.dir("rfg").get().asFile
    if (File(rfg, "minecraft-src/java/cpw").isDirectory) {
        logger.lifecycle("Clearing 1.7.10 leftovers from ${rfg.path} (branch switch)")
        rfg.deleteRecursively()
    }
}

// ---- RetroFuturaGradle / Minecraft configuration ----
minecraft {
    mcVersion.set("1.12.2")

    // Name shown for the dev-environment player
    username.set("Developer")

    // No extraTweakClasses, and specifically not MixinTweaker. RetroFuturaGradle
    // finds MixinBooter's own coremod on the classpath and loads it, and that is what
    // brings the Mixin subsystem up in the order it expects. Adding the tweaker as
    // well starts it a second time from a second class loader, and Forge's own ASM
    // transformers then fail to register with a loader constraint violation on Guava.
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
    maven {
        // MixinBooter — the 1.12.2 Mixin provider
        name = "CleanroomMC"
        url = uri("https://maven.cleanroommc.com/")
    }
}

dependencies {
    // MixinBooter bundles SpongePowered Mixin for 1.12.2 and supplies the
    // IEarlyMixinLoader hook our coremod uses. transitive = false: the published
    // POM pulls in a 1.12.2 Forge/MCP tree that would fight RFG's own.
    // In production the pack must also ship MixinBooter as a mod.
    implementation("zone.rong:mixinbooter:11.13") { isTransitive = false }

    // Add deobfuscated third-party mod jars here later if needed, e.g.:
    // implementation(rfg.deobf(project.files("libs/somejar.jar")))
}

// Classes and resources in one directory, for the dev run's benefit.
//
// Two separate problems meet here. Our jar is marked ForceLoadAsMod for production,
// and MixinBooter moves any such file onto FML's "reparseable coremods" list — which
// makes the classpath scan skip it, on the understanding that the mods folder scan
// will meet it again. In a dev run there is no copy in the mods folder, so nothing
// ever does: the coremod loads, the mixin applies, and the @Mod class is silently
// never constructed. FML scans every *directory* on the classpath unconditionally, so
// handing it a directory answers that.
//
// It has to be one directory, though. FML takes a mod's resource pack from whichever
// classpath entry it found the @Mod class in, and Gradle keeps classes and resources
// apart — so pointing it at both left it holding build/classes/java/main, which
// contains no assets/ at all. The "uky" resource domain never reached the resource
// manager and everything read through it quietly did nothing: sounds.json was never
// parsed, so every sound the mod plays was reported as an unknown soundEvent.
//
// A copy rather than redirecting the source set's own output directory: that made
// processResources and compileJava share a directory neither of them owned, and
// Gradle's stale-output cleanup then refused to run either of them.
val devClasspath = tasks.register<Sync>("devClasspath") {
    description = "Merges classes and resources into one directory for the dev run"
    from(sourceSets["main"].output)
    into(layout.buildDirectory.dir("devclasspath"))
}

// Register our coremod, which is what supplies the mixin config.
listOf(tasks.named<JavaExec>("runClient"), tasks.named<JavaExec>("runServer")).forEach { t ->
    t.configure {
        systemProperty("fml.coreMods.load", "com.console.uky.core.UkyCore")
        dependsOn(devClasspath)
        classpath(devClasspath.map { it.destinationDir })
    }
}

// The dev client opens at 854x480 by default, which is too small to judge a menu
// built around a full-width backdrop.
tasks.named<JavaExec>("runClient").configure {
    args("--width", "1280", "--height", "720")
}

// Coremod / mixin metadata for the built (production) jar. It has to go on `jar`
// rather than on `reobfJar`: the reobfuscation task remaps the jar it is given and
// writes the result itself, so a manifest configured on it is never used.
//
// The archive version is set here too: "ukyui-0.5.0.jar" tells a bug report nothing
// about which Minecraft it was built for, and this project already lives on two
// branches of that question. "ukyui-1.12.2-0.5.0.jar" is what actually distinguishes
// the two, and it is what shows up in a mod list or a download link either way.
// reobfJar (the file that ships) picks this up on its own: RetroFuturaGradle points
// its archiveVersion at jar's by convention, which is also why nothing has to be
// repeated for the "-dev" classifier that stays on the workspace jar alone.
tasks.named<Jar>("jar").configure {
    archiveVersion.set(minecraft.mcVersion.map { mc -> "$mc-${project.version}" })
    manifest {
        attributes(
            "FMLCorePlugin" to "com.console.uky.core.UkyCore",
            "FMLCorePluginContainsFMLMod" to "true",
            "ForceLoadAsMod" to "true",
            "MixinConfigs" to "mixins.uky.early.client.json"
        )
    }
}

// Deprecation detail is worth having on a version port: 1.12.2 deprecated a good
// deal of what 1.7.10 called normal, and a silent warning is a behaviour change
// nobody reads.
tasks.named<JavaCompile>("compileJava").configure {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation"))
}
