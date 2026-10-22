# 04 - Configuration and requirements

## 1. Runtime configuration

The mod has no configuration file, no environment variable and no command-line option of its own. Every source file in
`src/` and `forge/src/` was read for this spec and none reads any of those. The only settings are constants in code:

| Setting | Value | Where |
|---|---|---|
| Loopback port | `48291` | `src/client/java/com/exojosh/client/ThorHudClient.java:106`; `forge/src/main/java/com/exojosh/forge/ThorHudForgeClient.java:35` |
| Bind address | `127.0.0.1` only | `HudStateServer.java:78`; `forge/.../HudStateServer.java:146` |
| Bind retry interval | 5000 ms | `HudStateServer.java:33`; `forge/.../HudStateServer.java:27` |
| Protocol version | `1` | `HudStateServer.java:41`; `forge/.../HudStateServer.java:29` |
| Listen backlog | `4` (Fabric), `50` (Forge) | `HudStateServer.java:78`; `forge/.../HudStateServer.java:146` |
| Icon renders per tick | `2` | `ThorHudClient.java:74`; `forge/.../ThorHudForgeClient.java:27` |
| Map tile interval | 10 ticks | `ThorHudClient.java:81`; `forge/.../ThorHudForgeClient.java:28` |
| Chat history | 100 messages | `ChatRelay.java:72`; `forge/.../ChatRelayForge.java:24` |
| Icon size | 64 by 64 px (Fabric render) | `ItemIconRenderer.java:91` |
| Map tile size | 128 by 128 px | `MapRenderer.java:64`; `forge/.../MapRendererForge.java:28` |
| Forge container-screen suppression | `true`: container screens are cancelled on the top display until the app sends `SUPPRESS_CONTAINER:false` | `forge/.../ContainerRelayForge.java:29` |

Anything else the app needs (its own settings, its own reconnect timing) is in the app repository and is not described here.

The Gradle property `quickPlay` is a build-time option for `runClient` only. It sets `--quickPlaySingleplayer` in the dev
run (`build.gradle:40-44`). It does not affect the packaged mod.

## 2. Build configuration: Fabric (`build.gradle`, `gradle.properties`, `settings.gradle`)

| Item | Value | Source |
|---|---|---|
| Minecraft | 1.21.11 | `gradle.properties:10` |
| Yarn mappings | `1.21.11+build.6` (applied as `:v2`) | `gradle.properties:11`; `build.gradle:52` |
| Fabric Loader | 0.19.3 | `gradle.properties:12`; `build.gradle:53` |
| Fabric API | 0.141.5+1.21.11 | `gradle.properties:20`; `build.gradle:56` |
| Fabric Loom plugin | `net.fabricmc.fabric-loom-remap` version `1.17-SNAPSHOT` | `build.gradle:2`; `gradle.properties:13` |
| Java release | 21 (`options.release`, `sourceCompatibility`, `targetCompatibility`) | `build.gradle:68-70,81-82` |
| Gradle wrapper | 9.5.1 | `gradle/wrapper/gradle-wrapper.properties:3` |
| Mod version | 1.0.0 | `gradle.properties:16` |
| Maven group | `com.exojosh` | `gradle.properties:17` |
| Root project name | `aynthor_secondscreen_v1_21` | `settings.gradle:13` |

Points worth knowing:

- Loom is pinned to a `-SNAPSHOT` version. Anyone building later may get a different Loom. **Unverified** whether the
  snapshot is still resolvable.
- `splitEnvironmentSourceSets()` is on, so `src/main` runs on both sides and `src/client` is client-only
  (`build.gradle:17-18`). The mod's source sets are both attached (`build.gradle:20-25`).
- `runClient` is configured with an optional `--quickPlaySingleplayer` argument (`build.gradle:27-46`).
- The build deliberately has no sources jar (`build.gradle:72-83`). The comment explains that a second jar in `build/libs`
  could be imported by mistake, so exactly one jar is produced.
- The `jar` task copies `LICENSE` into the jar under a renamed path (`build.gradle:85-92`).
- `publishing` is set up with an empty repository list (`build.gradle:95-109`). Nothing is published from this repository.
- `org.gradle.configuration-cache=false` is set (`gradle.properties:6`). The `-Xmx1G` heap is set in `gradle.properties:2`.

### Mod metadata (`src/main/resources/fabric.mod.json`)

- Mod id `aynthor_secondscreen_v1_21`, version expanded from the build (`fabric.mod.json:3-4`).
- Entry points: `main` is `com.exojosh.Aynthor_secondscreen_v1_21`; `client` is `com.exojosh.client.Aynthor_secondscreen_v1_21Client`
  and `com.exojosh.client.ThorHudClient` (`fabric.mod.json:18-26`). The HUD logic is in `ThorHudClient`.
- Two mixin configs: the common one, `aynthor_secondscreen_v1_21.mixins.json`, and the client one,
  `aynthor_secondscreen_v1_21.client.mixins.json`, with `environment: client` (`fabric.mod.json:27-33`).
- Declared dependencies (`fabric.mod.json:34-39`): `fabricloader >= 0.19.3`, `minecraft ~1.21.11`, `java >= 21`, `fabric-api *`.
- License `CC0-1.0`, with the icon at `assets/aynthor_secondscreen_v1_21/icon.png` (`fabric.mod.json:15-16`).

### Mixin configs

- `src/main/resources/aynthor_secondscreen_v1_21.mixins.json` lists one class, `ExampleMixin`, in package `com.exojosh.mixin`
  (`:5-7`). The mixin injects an empty body at `MinecraftServer.loadWorld`. It is template boilerplate.
- `src/client/resources/aynthor_secondscreen_v1_21.client.mixins.json` lists `ExampleClientMixin` (empty injection at
  `MinecraftClient.run`), `KeyBindingAccessor` and `NativeImageInvoker`, in package `com.exojosh.client.mixin` (`:3-9`).
  `KeyBindingAccessor` is the only accessor the HUD logic depends on (`CommandDispatcher.java:185-189`).
- Both configs use `"compatibilityLevel": "JAVA_21"` and `"required": true`.

## 3. Build configuration: Forge (`forge/`)

| Item | Value | Source |
|---|---|---|
| Minecraft | 1.20.1 | `forge/gradle.properties:4` |
| Forge | 47.4.0 | `forge/gradle.properties:5` |
| ForgeGradle plugin | `net.minecraftforge.gradle` version `6.0.+` (a dynamic range) | `forge/build.gradle:7` |
| Mappings channel | `official` | `forge/build.gradle:22` |
| Java toolchain | 17 | `forge/build.gradle:19` |
| Mod id | `aynthor_secondscreen` | `forge/build.gradle:31,54`; `mods.toml:6` |
| Archive base name | `aynthor_secondscreen-forge-1.20.1` | `forge/build.gradle:17` |
| Gradle wrapper | 8.5 | `forge/gradle/wrapper/gradle-wrapper.properties:3` |
| Gradle JVM | `-Xmx3G`, daemon off | `forge/gradle.properties:1-2` |
| Forge settings name | `aynthor_secondscreen_forge` | `forge/settings.gradle:8` |

- The Forge build is a separate Gradle build under `forge/`. It is not part of the root build (`forge/settings.gradle`).
- The `run` directory for the dev client is `forge/run` (`forge/build.gradle:27`).
- `processResources` expands these values into `META-INF/mods.toml` and `pack.mcmeta` (`forge/build.gradle:47-66`):
  `minecraft_version_range` `[1.20.1,1.21)`, `forge_version_range` `[47,)`, the mod name and description, and authors
  `exojosh, venatrix-ritz`.
- The jar manifest sets `Specification-*`, `Implementation-*` and `Implementation-Timestamp` (`forge/build.gradle:68-81`).
  The `reobfJar` task runs after `jar` (`forge/build.gradle:80`). Reobfuscation is handled by ForgeGradle.
- `mods.toml` (`forge/src/main/resources/META-INF/mods.toml`) declares a `javafml` loader, the `forge` dependency and the
  `minecraft` dependency, both `side="CLIENT"` and mandatory (`mods.toml:13-25`). The `logoFile` is `icon.png`
  (`mods.toml:9`); the file is at `forge/src/main/resources/icon.png`.
- `pack.mcmeta` sets `pack_format` 15 (`forge/src/main/resources/pack.mcmeta:4`). This value is the Minecraft 1.20.1 pack format; **unverified** against the Forge build in this session.
- Forge dependency declared: only `net.minecraftforge:forge:1.20.1-47.4.0` (`forge/build.gradle:43-45`).

## 4. Dependencies (what the code imports)

| Dependency | Declared in this repo | Used by |
|---|---|---|
| Minecraft and mappings | Yes (Loom, ForgeGradle) | Everything |
| Fabric Loader, Fabric API | Yes (`build.gradle:53,56`) | Fabric side |
| Forge | Yes (`forge/build.gradle:44`) | Forge side |
| Gson (`com.google.gson.Gson`) | No | `HudStateServer` on both sides |
| Apache Commons Lang 3 (`StringUtils`) | No | `ChatRelay.java:10`; `ChatRelayForge.java:10` |
| Mojang or Yarn `StringHelper` / `StringUtil` | No (game classes) | chat sending |
| Java AWT and ImageIO | JDK | `MapRendererForge.java:17-19,141` |

Gson and Commons Lang are imported without being declared. The build assumes they come from the game's own classpath.
That is **unverified** here, because no build was run.

Python tools (`tools/`) need Python 3 for the capture harness, which uses only the standard library (`capture_protocol.py:22-28`).
`tools/make_icon.py` needs Pillow (`make_icon.py:13,17`; `README.md:129`).

## 5. Repository layout relevant to configuration

- `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew` and `gradle/` are the Fabric build.
- `forge/` is a complete second Gradle build with its own wrapper.
- `.github/workflows/build.yml` runs both builds (section 6).
- `.gitignore` excludes `build/`, `run/`, `.gradle/`, IDE folders and JFR and HPROF dumps (`.gitignore:1-40`).
  `forge/build/libs/` contains a Forge jar in a local working copy; it is ignored and was not built for this spec.

## 6. Continuous integration

`.github/workflows/build.yml`, triggered on every push and pull request (`:7`):

- **build** job on `ubuntu-24.04`: checkout, validate the Gradle wrapper, set up JDK 21 (distribution `microsoft`), run
  `./gradlew build`, and upload `build/libs/` as artifact `Artifacts` (`:10-35`).
- **forge** job on `ubuntu-24.04`: the same steps in `forge/` with JDK 17, and upload `forge/build/libs/` as artifact
  `forge-artifacts` (`:40-62`).

The workflow runs `build`, which compiles and packages. No test task runs, because there are no test sources
(section 05). The CI does not start Minecraft.

## 7. Runtime requirements

| Requirement | Source |
|---|---|
| Minecraft 1.21.11 with Fabric Loader 0.19.3 or newer, plus Fabric API (Fabric build) | `README.md:48-51`; `fabric.mod.json:34-39` |
| Minecraft 1.20.1 with Forge 47 or newer (Forge build) | `mods.toml:13-25` |
| Java 21 on the Fabric side, Java 17 on the Forge side | `build.gradle:68-70`; `forge/build.gradle:19` |
| A launcher that can import a Fabric or Forge jar (Zalith Launcher 2 named) | `README.md:46-47,55-67` |
| A second Android presentation display for the app | `README.md:44-45` |
| No conflicting process on port 48291 | `README.md:104`; `ThorHudClient.java:255-257` |
| Companion app present on the device (not in this repo) | `README.md:19-20` |

The README also says the app "needs no permissions and no setup" (`README.md:85-86`). That is a statement about the app, and is **unverified** here.
