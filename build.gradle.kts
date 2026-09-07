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
    val mcVersion = "1.12.2"
    val rfg = layout.buildDirectory.dir("rfg").get().asFile
    val foreignJar = rfg.listFiles { f: File ->
        f.isFile && f.name.matches(Regex("""(recompiled_minecraft|mclauncher)-(.+)\.jar"""))
                && !f.name.contains("-$mcVersion.")
    }?.firstOrNull()
    // net/minecraftforge/fml is 1.12.2's own FML and belongs here; cpw/ is 1.7.10's.
    val foreignFml = File(rfg, "minecraft-src/java/cpw").isDirectory

    if (foreignJar != null || foreignFml) {
        val reason = foreignJar?.name ?: "cpw/mods/fml in the sources"
        logger.lifecycle("Clearing non-$mcVersion leftovers from ${rfg.path} ($reason)")
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

    // The mod's own artwork, put at the root of the jar under the two names anything
    // that shows an icon for a mod knows to look for.
    //
    // "logo.png" is what mcmod.info's logoFile points at: FML's own mod list draws it,
    // and so do the launchers — the one in the screenshot already read the mod's *name*
    // out of mcmod.info, so it parses the file and was only ever missing the field.
    // "pack.png" is the resource pack icon: FML makes every mod a resource pack, so
    // this is the mod's face in the resource pack list, ours included.
    //
    // Copied from art/ rather than kept in the resources tree so the picture exists
    // once in the repository and the jar is where it gets duplicated.
    val icon = layout.projectDirectory.file("art/title.png").asFile
    from(icon) { rename { "logo.png" } }
    from(icon) { rename { "pack.png" } }

    // Checked rather than assumed. A `from` pointing at a file that is not there
    // copies nothing and says nothing, so the whole of the failure is a jar whose
    // icon is missing — which is exactly what the release workflow shipped while this
    // picture was still excluded from the repository and only existed on one machine.
    doFirst {
        if (!icon.isFile) {
            throw GradleException("art/title.png is missing, and the jar's icon comes "
                    + "from it. Restore the file rather than building without it: a jar "
                    + "with no icon is what a mod list and every launcher show a grey "
                    + "cube for.")
        }
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
            // Only the config that targets FML. mixins.uky.mods.client.json is
            // deliberately absent: this attribute is read while core mods load, and a
            // config registered then resolves its targets before ordinary mod jars are
            // on the classpath — which permanently poisons LaunchWrapper's lookup for
            // the class it failed to find. See LateMixins.
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
    // "0.5.4+1.12.2" rather than "0.5.4". Both branches of this repository release the
    // same mod version, and Modrinth wants a version number no other version of the
    // project already has — the second of the two would be rejected. The build metadata
    // suffix is what everybody else's multi-version releases use, and it says the same
    // thing the file name does.
    versionNumber.set(minecraft.mcVersion.map { mc -> "${project.version}+$mc" })
    // No versionName, for the same reason there is no displayName on the CurseForge
    // side: left unset it falls back to the version number, and the file underneath it
    // says the rest. A sentence there is a heading nobody asked for.
    versionType.set("release")
    // The task itself: Minotaur understands an archive task and takes the file off it,
    // which is one fewer thing to keep in step with where the build writes its jars.
    uploadFile.set(releaseJar)
    gameVersions.set(listOf("1.12.2"))
    loaders.set(listOf("forge"))
    changelog.set(provider { releaseNotes })
    // MixinBooter is what provides the mixin subsystem on 1.12.2, and this mod does
    // not start without it. Its Modrinth id goes here the way UniMixins' does on the
    // 1.7.10 branch — by id rather than by slug, because an owner can change a slug
    // and the id is what the API actually resolves. Left unset until that id is filled
    // in: a wrong id fails the upload, an absent block only omits the dependency.
    // dependencies {
    //     required.project("<mixinbooter-modrinth-id>")
    // }
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
        main.addGameVersion("1.12.2")
        // CurseForge wants a tag from its "environment" group as well as a game
        // version, and rejects the upload with error 1021 — "you must select at least
        // one version from the environment group" — when there is none. Client,
        // because that is what this mod is: it draws screens and touches no world
        // data, so a server has nothing to do with it.
        main.addEnvironment("Client")
        main.addModLoader("Forge")
        main.addJavaVersion("Java 8")
        main.addRequirement("mixinbooter")
    }
}

/** Both stores at once, which is what a release actually is. */
tasks.register("publishRelease") {
    group = "upload"
    description = "Publishes the release jar to Modrinth and CurseForge"
    dependsOn(tasks.named("modrinth"), tasks.named("curseforge"))
}

/**
 * The version, the Minecraft version and the notes, for whatever is driving a release
 * from outside Gradle.
 *
 * The release workflow reads all three from here rather than parsing this file or the
 * changelog itself, so the rules for what a version is and where its notes come from
 * live in exactly one place.
 *
 * The Minecraft version matters to it because that workflow builds either branch of
 * this repository on request, and which Minecraft a branch is for is the whole
 * difference between them — as well as being in the name of the file it uploads.
 * Asked rather than written down there, so a branch answers for itself.
 */
tasks.register("printVersion") {
    group = "help"
    description = "Prints the project version"
    val value = project.version.toString()
    doLast { println(value) }
}

tasks.register("printMcVersion") {
    group = "help"
    description = "Prints the Minecraft version this branch builds for"
    val value = minecraft.mcVersion.get()
    doLast { println(value) }
}

tasks.register("printReleaseNotes") {
    group = "help"
    description = "Prints this version's section of CHANGELOG.md"
    doLast { println(releaseNotes) }
}
