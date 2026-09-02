# Releasing

One tag publishes to Modrinth, CurseForge and GitHub. Everything those three are told —
the file, the version, the changelog — comes from this repository, so there is nothing
to type into a web form twice.

## Once, before the first release

**1. Create the two projects.** Neither can be created by an API token, so this part is
by hand:

- Modrinth — <https://modrinth.com/dashboard/projects>. Client-side, server-side
  unsupported, Forge, Minecraft 1.7.10. The store pages in `store/` already assume the
  slug `ukyui`.
- CurseForge — <https://legacy.curseforge.com/project/create>, under Minecraft →
  Mods → Server Utility / Cosmetic.

A Modrinth project has to be approved before its page is public. Uploads work while it
is still a draft, so this does not block anything below.

**2. Put the project identifiers in `gradle.properties`.** They are public, which is
why they are committed:

```properties
modrinthProjectId=ukyui
curseforgeProjectId=1234567
```

Modrinth takes the slug or the id. **CurseForge takes the numeric Project ID** from the
right-hand column of the project page — its slug is not accepted by the upload API.

**3. Add the two tokens as repository secrets** — Settings → Secrets and variables →
Actions → New repository secret:

| Secret             | Where it comes from                                                       |
| ------------------ | ------------------------------------------------------------------------- |
| `MODRINTH_TOKEN`   | <https://modrinth.com/settings/pats> — scopes: *Create versions*, and *Write projects* if you want `modrinthSyncBody` to work |
| `CURSEFORGE_TOKEN` | <https://legacy.curseforge.com/account/api-tokens>                         |

Tokens never go in a file in this repository. The build reads them from the
environment, and the workflow passes them in from the secrets above.

**4. Paste the store pages.** `store/modrinth-description.md` and
`store/curseforge-description.md` are the two project descriptions.

Modrinth's can be pushed from here:

```bash
MODRINTH_TOKEN=... ./gradlew modrinthSyncBody
```

CurseForge has no API for a project description — paste it in the web editor. The
image at the top of it wants a URL: upload `store/badges/row-combined.png` under the
project's Images tab, then replace `PASTE_FORGECDN_URL_OF_row-combined.png` with the
address CurseForge gives it.

## Every release

1. **Write the changelog.** Add a `## <version>` section at the top of
   `CHANGELOG.md`. That section is what both stores and the GitHub release show, so it
   is worth writing for a player rather than pasting commit subjects.
2. **Bump the version in both places** — `version` in `build.gradle.kts` and
   `UkyUI.VERSION` in `src/main/java/com/console/uky/UkyUI.java`. They have to agree;
   `checkModVersion` fails the release if they do not, before anything is uploaded.
3. **Tag and push:**

```bash
git tag v0.5.2 && git push origin v0.5.2
```

The workflow builds, checks the tag against the version, uploads to both stores and
creates the GitHub release with the jar attached. A store whose token is not configured
is skipped rather than failing the run.

### Choosing what to build

Running the workflow by hand from the Actions tab starts with **What to build**:

| Choice        | What it builds                                   |
| ------------- | ------------------------------------------------ |
| `this branch` | whichever branch the run was started from        |
| `1.12.2`      | the `1.12.2` branch                              |
| `1.7.10`      | the `1-7-10` branch                              |
| `both`        | both, one after the other                        |

Each is checked out and built on its own, and asks that branch for its own version and
its own Minecraft version — nothing about either is written down in the workflow, so a
branch that changes one of them needs nothing changed there.

`both` builds them one at a time rather than side by side, so that two runs cannot
create the same GitHub release at the same moment. When the two branches are on the same
version they share one release, with a jar each in it.

A tag push builds the commit the tag is on and nothing else, which is the branch the tag
was made on.

### Publishing to one place at a time

The same form has a tick box per destination — Modrinth, CurseForge, GitHub — and
publishes only to the ones ticked. With none ticked it builds and leaves the jar as a
workflow artifact, which is the way to rehearse.

That split is there for the case that actually happens: one destination accepts the
version and another rejects it. **An upload cannot be taken back** — a version that
reached Modrinth is on Modrinth, and sending it again makes a duplicate rather than a
correction. So finish a half-done release by re-running with only the destination that
failed ticked; the tick boxes are the whole mechanism for not publishing twice.

The GitHub release is the exception that is safe to repeat: it is created only if it is
not there and the jar is uploaded into it, so a second run adds the missing file rather
than failing.

## Publishing from this machine instead

The same tasks the workflow runs:

```bash
MODRINTH_TOKEN=... CURSEFORGE_TOKEN=... ./gradlew publishRelease
```

Individually: `./gradlew modrinth`, `./gradlew curseforge`, `./gradlew modrinthSyncBody`.
From here there is nothing to choose: the checked-out branch is what gets built. The
choice on the Actions tab exists because a runner can check out the other one.
Each checks its project id and its token before anything is built, so a missing one
fails in a second rather than at the end of a decompile.

## What goes where

| Thing              | Comes from                                                          |
| ------------------ | ------------------------------------------------------------------- |
| The file           | `build/libs/ukyui-<mc>-<version>.jar` — the reobfuscated jar, never the `-dev` one |
| Version number     | `version` in `build.gradle.kts`. Modrinth gets it with the Minecraft version on the end — `0.5.4+1.12.2` — because both branches release the same mod version and Modrinth will not take that number twice |
| Changelog          | this version's section of `CHANGELOG.md`                            |
| Game version       | the branch's own — `1.7.10` or `1.12.2` — Forge, Java 8, and on CurseForge the environment tag `Client`, without which the site rejects the upload with error 1021 |
| Dependency         | UniMixins on 1.7.10 (Modrinth `ghjoiQAl`, CurseForge `unimixins`), MixinBooter on 1.12.2 |
| Modrinth page body | `store/modrinth-description.md`, pushed by `modrinthSyncBody`       |

## Notes

- `gradle.properties` pins `org.gradle.java.home` to a path on the author's machine.
  The workflow strips that line before building; if anyone else clones this, it wants
  moving to their own `~/.gradle/gradle.properties` instead.
- The mod requires UniMixins at runtime. Both store listings declare it as a required
  dependency, and it is the first thing to check when a bug report says the game
  crashes on start-up.
