# 01 - Purpose and scope

## Purpose

Minecraft's HUD is moved off the top screen of a dual-screen Android handheld (the AYN Thor) and onto the bottom screen.
The game keeps running on the top screen with its HUD hidden for the elements that have been moved. The state behind
those elements is sent over a loopback socket to a companion app, which draws them on the bottom screen. Textures come
from the running game, so resource packs apply to the second screen too (`README.md:39-40`).

Two halves are required and neither works alone (`README.md:19-20`): this repository (the mod) and the companion app
(`README.md:16-17` names it as a separate repository; not in this tree).

## Scope

In scope, implemented in this repository:

- **Fabric 1.21.11 client mod** (`src/client/java/com/exojosh/client/`, entry points in `src/main/resources/fabric.mod.json:18-26`).
  Hides or wraps vanilla HUD elements, streams snapshots, serves textures and key bindings, relays chat, container
  contents and slot clicks, and renders item icons and map tiles.
- **Forge 1.20.1 port** (`forge/src/main/java/com/exojosh/forge/`). Covers the same loopback protocol with a
  narrower feature set. See [02-features-devices.md](02-features-devices.md) for the parity table.
- **Developer tooling**: `tools/capture_protocol.py` (socket capture harness) and `tools/make_icon.py` (icon generator).
  Neither was run for this spec.

Out of scope, or not in this tree:

- The Android companion app (drawing, gestures, reconnect loop, resource-pack lookup, SAF access). Described only by
  `README.md` and `thor-hud-handoff.md`. Everything about the app's behaviour is **unverified** here.
- The main (non-client) source set. `src/main/java/com/exojosh/Aynthor_secondscreen_v1_21.java:19-25` only logs a
  "Hello Fabric world!" line, and `src/main/java/com/exojosh/mixin/ExampleMixin.java:9-14` is an empty mixin on
  `MinecraftServer.loadWorld`. Both are template boilerplate (`CLAUDE.md`, "Icon request reliability" section, final paragraph).
- Texture serving on the Forge side. The Forge build has no asset catalog (see [02-features-devices.md](02-features-devices.md)).
- Launcher integration. Zalith Launcher 2 is named as the runtime (`README.md:46-47`); no code in this repository
  depends on it, and it is **unverified** how the launcher loads the jar.

## Users

- **Player** on an AYN Thor (or another Android device with a second presentation display) running Minecraft through
  Zalith Launcher 2 or another PojavLauncher-based launcher (`README.md:44-47`). They install the mod jar and the
  companion app, load a world, and use the bottom screen.
- **Developer** working on the mod. They build with Gradle, run `runClient`, and use the protocol harness.

## Workflows

### Install and first run (player)

1. Import the Fabric API and `aynthor_secondscreen_v1_21-1.0.0.jar` into a Fabric Loader 0.19.3 profile for 1.21.11
   (`README.md:55-67`). Fabric API is required; the mod declares `fabric-api: "*"` (`fabric.mod.json:38`).
2. Launch the companion app, then launch Minecraft and load a world (`README.md:88-97`).
3. The vanilla HUD disappears from the top screen and appears on the bottom screen. The mod binds the socket at client
   start, before any world is loaded (`ThorHudClient.java:131`), so the app can connect from the main menu.

### Asset and binding delivery (player-visible)

- On each new connection, the Fabric mod sends the texture bundle, then the key-binding list, the chat backlog, the
  container state and the last HUD snapshot (`ThorHudClient.java:321-355`). This runs on the client tick and only after
  a player exists; the early return for a missing player comes first (`ThorHudClient.java:142-146`). So a client that
  connects at the main menu gets the `hello` line but no assets or bindings until a world is loaded. The Forge build
  sends bindings, chat, the last snapshot and the container state, but no texture bundle (`ThorHudForgeClient.java:215-230`).
- Re-sending the textures after a resource-pack change is requested by the app with `ASSETS`. The mod has no reload
  hook (`ThorHudClient.java:35-37`).

### Moving a HUD element between screens

- The app sends `HUD:<key>,<key>,...` with the full set of elements that the game should draw. Anything not listed is
  drawn only on the bottom screen. The app re-sends the full set on every change and reconnect
  (`GameHudVisibility.java:97-107`, `ThorHudClient.java:179-181`). The default is empty, so the game draws nothing
  until told otherwise (`GameHudVisibility.java:77-78`).

### Leaving a world

- The mod sends `{"type":"noplayer"}` once, on the transition from having a player to not having one
  (`ThorHudClient.java:273-288`). The chat backlog is cleared with it, matching vanilla's chat clear on disconnect.

### Using a container

- While a container is open, the mod streams the open screen handler's slots (`ContainerRelay.java:118-147`). The app
  sends `SLOT:<syncId>,<slotId>,<button>,<ACTION>` for each click, and the mod performs it through the game's
  interaction manager (`ContainerRelay.java:179-222`). Slot clicks are accepted only when `syncId` matches the open handler.

### Chatting

- Incoming chat and game messages are sent to the app as coloured runs (`ChatRelay.java:89-100`). Outgoing text is sent with
  `CHAT:<text>`; a leading `/` is routed as a command (`ChatRelay.java:131-142`).

### Pressing a key binding

- The app sends `BIND:<id>` for any binding in its list, or a digit `1`-`9` for a hotbar slot. The press lasts one
  game tick (`CommandDispatcher.java:182-191`). Presses are dropped while a game screen is open
  (`CommandDispatcher.java:134-143`).

### Reporting a bind failure

- If port 48291 is taken, the mod retries every 5 seconds (`HudStateServer.java:33,114-119`). On Fabric it also writes a
  red message in the player's chat, once per failure (`ThorHudClient.java:244-259`). The Forge build writes a different,
  shorter chat message (`ThorHudForgeClient.java:167-180`).

## Developer workflows

- `./gradlew build` produces `build/libs/aynthor_secondscreen_v1_21-1.0.0.jar` (`README.md:114-115`). **Not run for this spec.**
- `./gradlew runClient`, optionally `-PquickPlay="New World"` to auto-join a world (`build.gradle:27-46`).
  **Not run for this spec.**
- Protocol capture: `python tools/capture_protocol.py --out captures` against a running client (`CLAUDE.md`, "Commands" section).
  **Not run for this spec.**
- Forge build: `cd forge && ./gradlew build` (`.github/workflows/build.yml:55-57`). **Not run for this spec.**
