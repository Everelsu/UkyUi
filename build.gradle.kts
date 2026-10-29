import net.darkhax.curseforgegradle.TaskPublishCurseForge

plugins {
    id("java-library")
    id("net.neoforged.moddev") version "2.0.148"
    // Publishing. Neither plugin does anything until its task is asked for by name;
    // see the release section at the bottom of this file.
    id("com.modrinth.minotaur") version "2.8.7"
    id("net.darkhax.curseforgegradle") version "1.1.26"
}

// ---- Project coordinates ----
//
// Keep `version` and UkyUI.VERSION together: checkModVersion below fails a release
// whose two disagree, and it is the mod list people quote in bug reports.
group = "com.console.uky"
version = "0.6.1"

val mcVersion = "1.21.1"
val neoVersion = "21.1.265"

base {
    archivesName.set("ukyui")
}

// Mojang ships Java 21 with 1.21.1.
java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "100000"))
}

neoForge {
    version = neoVersion

    parchment {
        minecraftVersion = mcVersion
        mappingsVersion = "2024.11.17"
    }

    runs {
        register("client") {
            client()
            // Not run/ itself: the 1.7.10 and 1.12.2 branches share this working
            // directory, and their run/mods would be loaded by this game too.
            gameDirectory.set(file("run/neoforge"))
            // The dev client opens at 854x480 by default, which is too small to judge
            // a menu built around a full-width backdrop.
            programArguments.addAll("--width", "1280", "--height", "720")
            systemProperty("uky.debugShots", providers.gradleProperty("ukyDebugShots").getOrElse("false"))
            systemProperty("uky.glCheck", providers.gradleProperty("ukyGlCheck").getOrElse("false"))
        }
    }

    mods {
        register("uky") {
            sourceSet(sourceSets.main.get())
        }
    }
}

// neoforge.mods.toml gets the version from here.
tasks.processResources.configure {
    val props = mapOf("modVersion" to project.version.toString(), "mcVersion" to mcVersion,
        "neoVersion" to neoVersion)
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }

    // The mod's own artwork, at the path neoforge.mods.toml's logoFile names. Copied
    // from art/ rather than kept in the resources tree so the picture exists once in
    // the repository and the jar is where it gets duplicated.
    val icon = layout.projectDirectory.file("art/title.png").asFile
    from(icon) { rename { "uky_logo.png" } }

    // Checked rather than assumed: a `from` pointing at a missing file copies nothing
    // and says nothing, and the release ships a jar with a grey cube for an icon.
    doFirst {
        if (!icon.isFile) {
            throw GradleException("art/title.png is missing, and the jar's icon comes "
                    + "from it. Restore the file rather than building without it.")
        }
    }
}

// "ukyui-1.21.1-0.6.1.jar": the file name says which Minecraft it is for, since this
// project lives on several branches of that question.
tasks.named<Jar>("jar").configure {
    archiveVersion.set("$mcVersion-${project.version}")
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

/** The jar that ships. NeoForge runs on Mojang names in production, so there is no reobf step. */
val releaseJar = tasks.named<Jar>("jar")

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
    // "0.5.4+1.21.1" rather than "0.5.4". Both branches of this repository release the
    // same mod version, and Modrinth wants a version number no other version of the
    // project already has — the second of the two would be rejected. The build metadata
    // suffix is what everybody else's multi-version releases use, and it says the same
    // thing the file name does.
    versionNumber.set("${project.version}+$mcVersion")
    // No versionName, for the same reason there is no displayName on the CurseForge
    // side: left unset it falls back to the version number, and the file underneath it
    // says the rest. A sentence there is a heading nobody asked for.
    versionType.set("release")
    // The task itself: Minotaur understands an archive task and takes the file off it,
    // which is one fewer thing to keep in step with where the build writes its jars.
    uploadFile.set(releaseJar)
    gameVersions.set(listOf(mcVersion))
    loaders.set(listOf("neoforge"))
    changelog.set(provider { releaseNotes })
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
        // — "ukyui-1.21.1-0.5.2.jar" — which already says the mod, the Minecraft
        // version and the mod version, in the form somebody downloading it is about to
        // see on disk anyway. A sentence in its place reads as a heading over a
        // download list that does not need one.
        main.changelog = releaseNotes
        main.changelogType = "markdown"
        main.addGameVersion(mcVersion)
        // CurseForge wants a tag from its "environment" group as well as a game
        // version, and rejects the upload with error 1021 — "you must select at least
        // one version from the environment group" — when there is none. Client,
        // because that is what this mod is: it draws screens and touches no world
        // data, so a server has nothing to do with it.
        main.addEnvironment("Client")
        main.addModLoader("NeoForge")
        main.addJavaVersion("Java 21")
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
    val value = mcVersion
    doLast { println(value) }
}

tasks.register("printReleaseNotes") {
    group = "help"
    description = "Prints this version's section of CHANGELOG.md"
    doLast { println(releaseNotes) }
}
