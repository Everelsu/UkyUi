import net.darkhax.curseforgegradle.TaskPublishCurseForge

plugins {
    id("java-library")
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.2"
    // Publishing. Neither plugin does anything until its task is asked for by name;
    // see the release section at the bottom of this file.
    id("com.modrinth.minotaur") version "2.8.7"
    id("net.darkhax.curseforgegradle") version "1.1.26"
}

// ---- Project coordinates ----
//
// Keep `version` and UkyUI.VERSION together. Forge reads the version from the @Mod
// annotation, which needs a compile-time constant and so cannot be given this one;
// mcmod.info gets it from here through the processResources filter below. If the two
// disagree, the mod list and the file name disagree, and it is the mod list people
// quote in bug reports.
group = "com.console.uky"
version = "0.5.4"

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
            // Only the config that targets FML. mixins.uky.mods.client.json is
            // deliberately absent: this attribute is read while core mods load, and a
            // config registered then resolves its targets before ordinary mod jars are
            // on the classpath — which permanently poisons LaunchWrapper's lookup for
            // the class it failed to find. See LateMixins.
            "MixinConfigs" to "mixins.uky.early.client.json"
        )
    }
}

// ---- Release: Modrinth and CurseForge ----
//
// Both stores are wired to the same three things — the reobfuscated jar, the version
// in `version` above, and the notes for that version in CHANGELOG.md — so a release is
// one tag rather than two forms filled in twice.
//
// Nothing here runs on its own: `build` depends on none of it, and the tasks
// (`modrinth`, `modrinthSyncBody`, `curseforge`, `publishRelease`) each have to be
// asked for by name. Credentials are read from the environment and never from a file
// in this repository — MODRINTH_TOKEN and CURSEFORGE_TOKEN, which in CI come from the
// repository secrets of the same name. See RELEASING.md.

/**
 * The jar that ships — reobfuscated to SRG names, not the workspace "-dev" one.
 *
 * Typed as the base `org.gradle.jvm.tasks.Jar` on purpose: RetroFuturaGradle's
 * reobfuscation task extends that rather than the `bundling.Jar` that a bare `Jar`
 * means in the Kotlin DSL, so asking for the latter fails at configuration time.
 */
val releaseJar = tasks.named<org.gradle.jvm.tasks.Jar>("reobfJar")

/**
 * Project identifiers, from gradle.properties.
 *
 * Empty by default, and checked when a publish runs rather than here: a fork with no
 * projects of its own must still be able to run every other task in this build, and
 * `gradle tasks` configures these two along with the rest.
 */
val modrinthProjectId: String = providers.gradleProperty("modrinthProjectId").getOrElse("")
val curseforgeProjectId: String = providers.gradleProperty("curseforgeProjectId").getOrElse("")

val modrinthToken: String = providers.environmentVariable("MODRINTH_TOKEN").getOrElse("")
val curseforgeToken: String = providers.environmentVariable("CURSEFORGE_TOKEN").getOrElse("")

/**
 * What changed in this version, read out of CHANGELOG.md.
 *
 * The section headed with this version, down to the next heading. Written once and
 * sent to both stores and to the GitHub release, because three copies of a changelog
 * are three different changelogs by the second release.
 */
val releaseNotes: String by lazy {
    val changelog = file("CHANGELOG.md")
    val fallback = "https://github.com/Everelsu/UkyUi/releases/tag/v${project.version}"
    if (!changelog.isFile) {
        return@lazy fallback
    }
    val text = changelog.readText()
    val heading = Regex("(?m)^##\\s+\\[?" + Regex.escape(project.version.toString()) + "]?.*$")
        .find(text) ?: return@lazy fallback
    val rest = text.substring(heading.range.last + 1)
    val next = Regex("(?m)^##\\s+").find(rest)
    (if (next == null) rest else rest.substring(0, next.range.first)).trim().ifEmpty { fallback }
}

/**
 * Fails a release whose version does not agree with the one compiled into the mod.
 *
 * `UkyUI.VERSION` has to be a compile-time constant, so it cannot be given the value
 * from this file, and the two drifting apart is invisible until somebody quotes the
 * mod list in a bug report against a jar that says something else.
 */
val checkModVersion = tasks.register("checkModVersion") {
    group = "verification"
    description = "Checks UkyUI.VERSION against the version in build.gradle.kts"
    val source = file("src/main/java/com/console/uky/UkyUI.java")
    val expected = project.version.toString()
    inputs.file(source)
    inputs.property("version", expected)
    doLast {
        val found = Regex("VERSION\\s*=\\s*\"([^\"]+)\"").find(source.readText())?.groupValues?.get(1)
        check(found == expected) {
            "Version mismatch: build.gradle.kts says $expected, UkyUI.VERSION says $found.\n" +
                "Both have to move together — see the note at the top of this file."
        }
    }
}

/**
 * Everything a publish needs set up, checked before anything is built.
 *
 * A task of its own, with the jar ordered after it, so a missing token fails in a
 * second rather than at the end of a five-minute decompile — which is exactly when it
 * would happen on a fresh checkout.
 */
fun preflight(name: String, store: String, projectId: String, propertyName: String,
              tokenName: String, token: String) = tasks.register(name) {
    group = "verification"
    description = "Checks the $store release settings"
    dependsOn(checkModVersion)
    doLast {
        require(projectId.isNotEmpty()) {
            "$propertyName is not set. Put the project's id in gradle.properties" +
                " — see RELEASING.md."
        }
        require(token.isNotEmpty()) {
            "$tokenName is not set in the environment. It is a secret and does not" +
                " belong in this repository — see RELEASING.md."
        }
    }
}

val modrinthPreflight = preflight("checkModrinthRelease", "Modrinth", modrinthProjectId,
    "modrinthProjectId", "MODRINTH_TOKEN", modrinthToken)
val curseforgePreflight = preflight("checkCurseForgeRelease", "CurseForge", curseforgeProjectId,
    "curseforgeProjectId", "CURSEFORGE_TOKEN", curseforgeToken)

releaseJar.configure {
    // Only ever relevant when a preflight is in the task graph at all, which is to say
    // when something is being published. An ordinary build is untouched by this.
    mustRunAfter(modrinthPreflight, curseforgePreflight)
}

modrinth {
    token.set(modrinthToken)
    projectId.set(modrinthProjectId)
    versionNumber.set(project.version.toString())
    // No versionName, for the same reason there is no displayName on the CurseForge
    // side: left unset it falls back to the version number, and the file underneath it
    // says the rest. A sentence there is a heading nobody asked for.
    versionType.set("release")
    // The task itself: Minotaur understands an archive task and takes the file off it,
    // which is one fewer thing to keep in step with where the build writes its jars.
    uploadFile.set(releaseJar)
    gameVersions.set(listOf("1.7.10"))
    loaders.set(listOf("forge"))
    changelog.set(provider { releaseNotes })
    // ghjoiQAl is UniMixins — the coremod providing the mixin subsystem this mod does
    // not start without. By id rather than by slug: an owner can change a slug, and
    // the id is what the API actually resolves.
    dependencies {
        required.project("ghjoiQAl")
    }
    // Pushed by `modrinthSyncBody`, which is a task of its own; publishing a version
    // does not touch the project page.
    syncBodyFrom.set(provider { file("store/modrinth-description.md").readText() })
}

tasks.named("modrinth").configure {
    dependsOn(modrinthPreflight, releaseJar)
}

tasks.named("modrinthSyncBody").configure {
    dependsOn(modrinthPreflight)
}

tasks.register<TaskPublishCurseForge>("curseforge") {
    group = "upload"
    description = "Publishes the release jar to CurseForge"
    dependsOn(curseforgePreflight, releaseJar)

    apiToken = curseforgeToken

    // Only described when there is somewhere to send it. `gradle tasks` configures
    // every task it lists, and an upload declared against an empty project id fails
    // there — on a build that was never going to publish anything.
    if (curseforgeProjectId.isNotEmpty()) {
        val main = upload(curseforgeProjectId, releaseJar.get())
        main.releaseType = "release"
        // No displayName on purpose. Left unset, CurseForge shows the file's own name
        // — "ukyui-1.7.10-0.5.2.jar" — which already says the mod, the Minecraft
        // version and the mod version, in the form somebody downloading it is about to
        // see on disk anyway. A sentence in its place reads as a heading over a
        // download list that does not need one.
        main.changelog = releaseNotes
        main.changelogType = "markdown"
        main.addGameVersion("1.7.10")
        // CurseForge wants a tag from its "environment" group as well as a game
        // version, and rejects the upload with error 1021 — "you must select at least
        // one version from the environment group" — when there is none. Client,
        // because that is what this mod is: it draws screens and touches no world
        // data, so a server has nothing to do with it.
        main.addEnvironment("Client")
        main.addModLoader("Forge")
        main.addJavaVersion("Java 8")
        main.addRequirement("unimixins")
    }
}

/** Both stores at once, which is what a release actually is. */
tasks.register("publishRelease") {
    group = "upload"
    description = "Publishes the release jar to Modrinth and CurseForge"
    dependsOn(tasks.named("modrinth"), tasks.named("curseforge"))
}

/**
 * The version and the notes, for whatever is driving a release from outside Gradle.
 *
 * The release workflow reads both from here rather than parsing this file or the
 * changelog itself, so the rules for what a version is and where its notes come from
 * live in exactly one place.
 */
tasks.register("printVersion") {
    group = "help"
    description = "Prints the project version"
    val value = project.version.toString()
    doLast { println(value) }
}

tasks.register("printReleaseNotes") {
    group = "help"
    description = "Prints this version's section of CHANGELOG.md"
    doLast { println(releaseNotes) }
}
