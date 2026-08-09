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
version = "0.5.1"

// Java 8 toolchain is mandatory for 1.7.10 (both compiling and running)
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
// recompile then fails on a few hundred errors that all look like the mod's fault and
// none of which are.
//
// Two independent tells, because the directory can be caught at any stage of a build:
// the version is written into the jar names RFG leaves beside the sources, and the two
// versions put FML in different packages — 1.7.10 in cpw.mods.fml, 1.12.2 in
// net.minecraftforge.fml — so a tree carrying the other one cannot be ours. Either is
// unambiguous, so the leftovers are cleared rather than reported: fixing it by hand is
// one delete and five minutes of decompiling, every single time.
run {
    val mcVersion = "1.7.10"
    val rfg = layout.buildDirectory.dir("rfg").get().asFile
    val foreignJar = rfg.listFiles { f: File ->
        f.isFile && f.name.matches(Regex("""(recompiled_minecraft|mclauncher)-(.+)\.jar"""))
                && !f.name.contains("-$mcVersion.")
    }?.firstOrNull()
    // cpw/ is 1.7.10's own FML and belongs here; net/minecraftforge/fml is 1.12.2's.
    val foreignFml = File(rfg, "minecraft-src/java/net/minecraftforge/fml").isDirectory

    if (foreignJar != null || foreignFml) {
        val reason = foreignJar?.name ?: "net/minecraftforge/fml in the sources"
        logger.lifecycle("Clearing non-$mcVersion leftovers from ${rfg.path} ($reason)")
        rfg.deleteRecursively()
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

    // ---- Foreign-version mods in run/mods ----
    //
    // The same trap as the stale decompile above, one directory over: this repository
    // has a 1.7.10 branch and a 1.12.2 branch sharing one working directory, and
    // run/mods is not cleared by checking the other branch out. A jar for the wrong
    // Minecraft does not fail politely there. FML's own optifine probe is the worst
    // case — it reads a field off a class named "Config", resolving that field's type
    // drags in an obfuscated class the other version does not have, and the
    // NoClassDefFoundError this throws is an Error, which its `catch (Exception)`
    // does not catch. The game dies in beginMinecraftLoading with "NoClassDefFoundError:
    // hh" and no hint of which file caused it.
    //
    // Only ever a warning. run/ is the developer's own playground, and a build script
    // that deletes things out of it would be a worse surprise than the one it prevents.
    doFirst {
        val mcVersion = minecraft.mcVersion.get()

        // Matched against Minecraft versions that actually shipped, rather than
        // anything shaped like one. A mod's own version is shaped exactly like one —
        // "Waila-1.8.14", "angelica-2.1.59" — and a warning that cries wolf on half
        // the folder is a warning nobody reads the day it is right. Unknown versions
        // are simply not flagged, which is the safe direction for a hint.
        val released = setOf(
            "1.7.10", "1.8.9", "1.9.4", "1.10.2", "1.11.2", "1.12.2",
            "1.16.5", "1.18.2", "1.19.2", "1.20.1", "1.21.1"
        )
        // Delimited, so "1.8" inside "1.8.14" is not read as a version of its own.
        val version = Regex("""(?<![\d.])1\.\d+(\.\d+)?(?![\d.])""")

        val foreign = project.file("run/mods").listFiles { f: File ->
            f.isFile && f.name.endsWith(".jar")
                    && version.findAll(f.name)
                        .map { it.value }
                        .any { it in released && it != mcVersion }
        }?.sortedBy { it.name }.orEmpty()

        if (foreign.isNotEmpty()) {
            logger.warn("")
            logger.warn("WARNING: run/mods holds jars built for another Minecraft than $mcVersion:")
            foreign.forEach { logger.warn("    ${it.name}") }
            logger.warn("These rarely fail politely. An OptiFine for the wrong version takes the")
            logger.warn("game down in FML's own start-up with \"NoClassDefFoundError: hh\" and no")
            logger.warn("mention of the file. Move them out of run/mods.")
            logger.warn("")
        }
    }
}

// Coremod / mixin metadata for the built (production) jar. It has to go on `jar`
// rather than on `reobfJar`: the reobfuscation task remaps the jar it is given and
// writes the result itself, so a manifest configured on it is never used.
//
// The archive version is set here too: "ukyui-0.5.0.jar" tells a bug report nothing
// about which Minecraft it was built for, and this project already lives on two
// branches of that question. "ukyui-1.7.10-0.5.0.jar" is what actually distinguishes
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
