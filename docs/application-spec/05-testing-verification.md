# 05 - Testing and verification

This file describes how the repository is checked. Nothing here was run for this spec. The statements are based on the
files that exist in the tree on `origin/master`.

## 1. Automated tests

- **None.** No test sources exist under `src/`, `forge/src/` or elsewhere in the tree. A search for `@Test` and `junit`
  across the repository returned no files.
- No test task is configured in `build.gradle`, `forge/build.gradle` or `settings.gradle`, and no test dependency is declared.
- The CI workflow runs `./gradlew build` and `./gradlew build` in `forge/` (`.github/workflows/build.yml:30,56`). Both
  compile and package. Neither starts Minecraft nor exercises the socket.

The consequence is that the only automated guarantee is that both builds compile. Behaviour, wire format and
compatibility with the companion app are checked only by hand.

## 2. What CI checks

| Check | Where | What it proves |
|---|---|---|
| Gradle wrapper validity | `.github/workflows/build.yml:15-16,45-46` | The wrapper jar and properties match the Gradle distribution checksum |
| Fabric compile and package | `build.yml:29-30` | Java 21 sources compile against Minecraft 1.21.11 with Yarn mappings, and the jar is produced |
| Forge compile and package | `build.yml:55-56` | Java 17 sources compile against Forge 1.20.1 and the mods.toml expansion works |
| Artifact upload | `build.yml:31-35,58-62` | The jars are attached to the run. Nothing checks that exactly one jar was produced on the Fabric side, although `build.gradle:72-83` is written to ensure it |

Compilation does not confirm that the Yarn or Mojang method names used in the code do what the code assumes. The
handoff document and `CLAUDE.md` both say several names were compile-checked one at a time and never tested together
(`CLAUDE.md`, "Known unverified/fragile areas"; `thor-hud-handoff.md`, section 5). The current state of that check is
**unverified**.

## 3. Manual protocol checks (described, not run)

### 3.1 Raw socket

With `./gradlew runClient` running and a world joined:

```
nc localhost 48291
```

The README says one JSON line appears per change (`README.md:119-126`). The first line is `hello`. Then HUD snapshots
appear when the state changes. `CLAUDE.md` says "one JSON line per tick" and is out of date on this point; the code
sends on change (`ThorHudClient.java:224-228`).

### 3.2 Capture harness: `tools/capture_protocol.py`

Connects like the app, collects everything for up to 25 seconds (or until the chosen conditions are met), writes map
PNGs and icon PNGs to the output directory, and prints a summary. Options (`capture_protocol.py:31-61`):

| Option | Purpose |
|---|---|
| `--host`, `--port` | Defaults to `127.0.0.1:48291` |
| `--out` | Output directory, default `captures` |
| `--seconds` | Listen time, default 25 |
| `--connect-timeout` | Retry period for the connection, default 180 seconds |
| `--icon ITEM_ID` | Request an icon. Repeatable. Icon requests are sent after a 1-second pause (`capture_protocol.py:113-121`) |
| `--wait-noplayer` | Stay connected until a `noplayer` message arrives |
| `--move FROM,TO` | Send two `PICKUP` clicks on the open handler and print the contents before and after |
| `--say TEXT` | Send `CHAT:<text>` and wait for the echo as a chat message |

The summary prints: HUD state line count, map tile count, binding count, `noplayer` count, the `hello` line, chat
messages with their colour runs, container states before and after a move (and whether contents changed), icon results,
and the first HUD state (`capture_protocol.py:223-284`). It also checks the map marker position against the tile
bounds and flags out-of-bounds markers (`capture_protocol.py:286-297`).

The harness does not assert. A failed check is a line of printed output that a person must read. Exit status is 0
unless it could not connect (`capture_protocol.py:74-76,301-302`).

### 3.3 Documented end-to-end checks

`CLAUDE.md` ("Commands" section) describes three runs as the intended smoke test:

1. `./gradlew runClient -PquickPlay="New World"` followed by `python tools/capture_protocol.py --out captures`.
2. The same with `--say "hello"`, to exercise chat both ways.
3. The `--move` option, to exercise container clicks.

These were not run for this spec. Whether they pass on the current code is **unverified**.

## 4. Icon generator

`tools/make_icon.py` draws the mod icon at 64 by 64 and scales it to 128 by 128 with nearest-neighbour
(`make_icon.py:19-20,161`). It writes to `src/main/resources/assets/aynthor_secondscreen_v1_21/icon.png`. It needs Pillow.
The script was not run for this spec.

## 5. Device and companion-app testing

- The app is not in this tree. Device tests, screen captures and gesture tests are done outside this repository. **Unverified** here.
- `thor-hud-handoff.md` lists items that were never confirmed on the device, including icon fallbacks and hotbar
  alignment (section 5). Those remain open as far as this tree shows.

## 6. What the tree cannot show

- That the Fabric and Forge builds run in Minecraft. No run has been recorded in this repository.
- That the Yarn and Mojang API calls behave as the comments say. These are marked in the code and in `CLAUDE.md`.
- That the companion app parses every field correctly. No schema or fixture exists in this tree.
- That the Forge `ModHudCompat` reflection finds its target classes in a running modpack. The reflection is written to fail quietly.
