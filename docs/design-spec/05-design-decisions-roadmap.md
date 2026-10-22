# 05 - Design decisions and roadmap

Decisions below are the ones the code and its comments record. Where a comment gives a rejected alternative, it is
listed. Where no reason is written down, the entry says so, rather than supplying one. The roadmap is built from
`thor-hud-handoff.md` section 6, from the code's own TODO-style notes, and from gaps found while writing these specs.
Roadmap items are proposals, not commitments made in the repository.

## 1. Decisions

### D1. Loopback TCP, not shared memory
- **Decision:** the mod and the app talk over `127.0.0.1:48291`.
- **Reason recorded:** the two processes run in separate Android sandboxes with different UIDs, so loopback TCP was
  the simplest channel (`CLAUDE.md`, "Why a socket, not shared memory"; `thor-hud-handoff.md` section 1).
- **Alternatives:** shared memory, which the sandboxing rules out, and a launcher-level integration (`thor-hud-handoff.md`
  section 1 says the standalone app avoids one).

### D2. Newline-delimited JSON, nothing else
- **Decision:** one object per line, no length prefix, no envelope.
- **Reason recorded:** "no framing beyond newlines. Good enough for one companion app talking to one game instance"
  (`HudStateServer.java:21-27`).

### D3. Wrap HUD elements, do not remove them
- **Decision:** `HudElementRegistry.replaceElement` hands the original element back to the wrapper, which draws it
  only when told (`GameHudVisibility.java:16-30`).
- **Rejected:** `removeElement`, which is one-way. Nothing keeps the vanilla renderer, so the element cannot come back.
- **Forge:** cancels the overlay event (`GameHudVisibilityForge.java:45-60`). No reason is written in the Forge code.
  Given that Forge's event can only cancel, the choice follows from the platform; this is an inference.

### D4. The full set of shown-in-game elements, every time
- **Decision:** `HUD:<keys>` carries the whole set, re-sent on every change and reconnect
  (`GameHudVisibility.java:97-107`).
- **Reason recorded:** a dropped or reordered delta would leave the two screens disagreeing about who draws an element,
  and that shows as one element drawn twice or not at all.

### D5. Snapshots sent on change, not every tick
- **Decision:** `broadcastHudStateIfChanged` compares to the last sent record (`ThorHudClient.java:206-228`).
- **Reason recorded:** the old per-tick broadcast sent identical lines most of the time. A measurement in the code's
  comment says "2 lines in 20 seconds idle against about 400 before" (`CLAUDE.md`, "Architecture"); the measurement's
  method is not recorded and is **unverified**.
- **Consequence:** the last snapshot is kept for newcomers (`ThorHudClient.java:348-350`).

### D6. The container relay reads the open handler, not the inventory
- **Decision:** `player.currentScreenHandler` (`ContainerRelay.java:19-26`).
- **Reason recorded:** a move is a click on a slot, and the server accepts clicks only against the handler it believes
  is open. With no screen open, the handler is the player's own inventory, so one code path covers both.

### D7. Send the slot roles, do not make the app derive them
- **Decision:** `playerStart`, `hotbarStart`, `armorStart` and `offhandIndex` are on the wire (`ContainerRelay.java:39-45`).
- **Reason recorded:** vanilla's "last 36 slots" convention holds for every vanilla container, but knowing it is the mod's job.

### D8. Send raw values, not HUD decisions
- **Decision:** air in ticks, `maxAir`, `gameMode`, and the heart set as a name, not as a bubble count or a show flag
  (`HudState.java:17-22,37-46`).
- **Reason recorded:** "the HUD decision belongs to whatever draws the HUD." Vanilla's own rounding then runs on the app side.

### D9. Command codes name actions, not keys
- **Decision:** `SWAP`, `DROP`, `USE` and so on (`CommandDispatcher.java:33-40`).
- **Reason recorded:** old codes named keys, and two were wrong (`R` bound to swap-hands, `G` to drop). Naming keys
  also fails for mouse buttons and for rebinds. Old letters are kept as aliases with the behaviour they had.

### D10. A binding id is the wire identity
- **Decision:** `BIND:<translation key>`, resolved through `KeyBinding.byId` (`CommandDispatcher.java:24-27`).
- **Reason recorded:** no table has to know about another mod's binding, so modded bindings work without an entry.

### D11. Enumerate `options.allKeys`, not a private map
- **Decision:** the public array, which already includes mod bindings (`KeyBindingCatalog.java:26-30`).
- **Rejected:** an `@Accessor` on `KeyBinding.KEYS_BY_ID`, which would need a mixin for the same set.

### D12. A simulated press sets both the counter and the held flag
- **Decision:** `timesPressed` is incremented through `KeyBindingAccessor` and `setPressed(true)` is called
  (`CommandDispatcher.java:41-56,182-191`).
- **Reason recorded:** discrete actions read `wasPressed()`, which the flag alone does not feed.
- **Rejected:** `setPressed(true)` alone, which "silently did nothing for any of them" (`CommandDispatcher.java:51-54`).

### D13. Presses are dropped while a screen is open; chat and slot clicks are not
- **Decision:** `CommandDispatcher.dispatch` returns when `currentScreen != null` (`:134-143`). `CHAT:` and `SLOT:` are
  handled before the dispatcher (`ThorHudClient.java:182-188`).
- **Reason recorded:** queued presses would all fire when the screen closes. Chat is a packet, not a key, and a slot
  click is only meaningful while a screen is open (`ThorHudClient.java:59-67,184-188`).

### D14. Icons are rendered offscreen with the game's pipeline; the glint is drawn by the app
- **Decision:** `ItemIconRenderer` draws to a private texture and reads it back (`ItemIconRenderer.java:84-151`).
  Glint is passed as `Glint.NONE` (`:260`).
- **Reason recorded for the render:** the raw texture cannot represent isometric models, tints, or multi-face blocks
  (`ItemIconRenderer.java:38-52`).
- **Reason recorded for the glint:** passing the real type drew the item's layer twice, and the second layer covered
  the item. A still PNG would also freeze the shimmer (`:221-243`).
- **Rejected:** drawing through the GUI's own flush, which targets the main framebuffer. The comment at
  `ItemIconRenderer.java:63-66` says the earlier investigation concluded that was a dead end. The output texture
  override is used instead (`:58-66`). Nothing reaches the window framebuffer (`:73-75`).

### D15. Textures come from the game's resource manager, keyed by short names
- **Decision:** `HudAssetCatalog` maps keys to vanilla paths and reads through the resource manager
  (`HudAssetCatalog.java:17-32`).
- **Reason recorded:** resource packs apply automatically; no Mojang assets live in the app; a version bump changes one file.

### D16. A hello line identifies the mod
- **Decision:** the first line on every connection is `{"type":"hello",...}`, written before the client is registered
  (`HudStateServer.java:35-37,87-90`).
- **Reason recorded:** adbd on the same port accepts and drops connections, which looks like a flapping app.

### D17. Keep retrying the port rather than give up
- **Decision:** a 5-second retry (`HudStateServer.java:61-75`).
- **Reason recorded:** the old code returned from the accept loop on the first bind failure, which killed the second
  screen until the game restarted.

### D18. Hook chat at the receive events, not with a mixin into the chat HUD
- **Decision:** `ClientReceiveMessageEvents.CHAT` and `.GAME` (`ChatRelay.java:19-28,89-100`).
- **Reason recorded:** the events fire where vanilla decides a message is shown, so another mod's suppression also
  hides it on the second screen.

### D19. Chat backlog of 100
- **Decision:** `HISTORY_LIMIT = 100` (`ChatRelay.java:63-72`).
- **Reason recorded:** the same size as vanilla's chat history, so the second screen shows the same amount.

### D20. Map: vanilla shading, with two deliberate changes
- **Decision:** the vanilla brightness thresholds, with a per-world-coordinate checkerboard, and a ceiling scan
  from the player's height (`MapRenderer.java:28-59`).
- **Reason recorded:** a tile that re-centres every update would shimmer with pixel-based dithering. The heightmap is a
  bedrock roof in the Nether, so the map scans down from the player instead.

### D21. Icon fallback ladder
- **Decision:** render, then texture file, then an explicit failure (`ThorHudClient.java:386-409`).
- **Reason recorded:** "every request gets an answer" (`CLAUDE.md`, "Icon request reliability"). Silent drops made icons look flaky.

### D22. Forge reads mod APIs through reflection, not by depending on them
- **Decision:** TACZ, LSO and ParCool are read by name (`ModHudCompat.java:25-35`).
- **Reason recorded:** the Forge build's dependencies are Forge alone (`ModHudCompat.java:25-35`; `forge/build.gradle:43-45`).

### D23. Forge hides container screens by default
- **Decision:** `suppressRendering = true` (`ContainerRelayForge.java:29`).
- **Reason recorded:** none in the code. The default's purpose (the app draws containers itself) is an inference from
  the `SUPPRESS_CONTAINER` command's name and is **unverified**.

### D24. Client-side only, on both loaders
- **Decision:** Forge registers its listeners only on `Dist.CLIENT` (`AynThorSecondScreenForge.java:16-23`). The Fabric
  HUD classes live in the client source set (`build.gradle:18`).
- **Reason recorded:** the mod is a HUD mod, and a server has no HUD (`CLAUDE.md`, "Commands").

### D25. One jar, no sources jar
- **Decision:** `withSourcesJar()` is not used (`build.gradle:72-83`).
- **Reason recorded:** the launcher import dialog accepts whichever jar is chosen. A sources jar imported by mistake
  fails without any message, and the second screen stays dark.

## 2. Rejected alternatives recorded in code comments

| Alternative | Why rejected | Source |
|---|---|---|
| Per-tick HUD broadcast | Identical output most of the time | `ThorHudClient.java:206-223` |
| Queued presses while a screen is open | All fire at once when the screen closes | `CommandDispatcher.java:134-139` |
| Passing the real glint type to the offscreen item render | Duplicate draw; shimmer frozen in a still PNG | `ItemIconRenderer.java:221-243` |
| Reading the container from the player's inventory | Clicks are checked against the open handler | `ContainerRelay.java:19-26` |
| Mixin into `ChatHud.addMessage` | Suppressed messages would still reach the second screen | `ChatRelay.java:19-26` |
| `KEYS_BY_ID` accessor | Needs a mixin for the same set | `KeyBindingCatalog.java:26-30` |
| Setting only the held flag | Discrete actions never see it | `CommandDispatcher.java:51-54` |
| Keyboard-key codes | Mouse buttons, rebinds, and the old `R`/`G` mismatch | `CommandDispatcher.java:33-40` |
| Compositing the glint into a still icon | Frozen animation | `ItemIconRenderer.java:235-238` |
| Sending assets from the app's own bundled copies | Mojang assets would sit in the app's repository | `HudAssetCatalog.java:17-24` |
| Silent drop for unresolvable icon requests | Looked like flaky icons | `ThorHudClient.java:381-384` |

## 3. Roadmap

Each item says where it came from. Items marked **proposal** are not in the code or the handoff; they follow from
the gaps this spec found.

| # | Item | Source | Why |
|---|---|---|---|
| R1 | Serve textures from Forge: implement the `ASSETS` path and the asset bundle | `ThorHudForgeClient.java:96-97` (no-op) | An app on Forge has no textures without it |
| R2 | Port the offscreen icon renderer to Forge, or document why not | `ItemIconResolverForge.java` (texture only) | Forge icons are flat, and blocks with no single texture come out wrong |
| R3 | Reply to `ICON:minecraft:air` and empty ids | `ThorHudClient.java:364`; `CLAUDE.md` says every request gets an answer | The app waits for a reply that never comes |
| R4 | Serve the damage-flash heart variants | `HudAssetCatalog.java:53-54` (open item) | The flash animation is missing on the second screen |
| R5 | Remove the legacy single-letter codes once the app is updated | `CommandDispatcher.java:101-105` | Stated in the code's own comment |
| R6 | Align the Forge and Fabric record shapes, or write down the difference | `forge/.../HudState.java`, `ContainerRelayForge.java:32-64` | An app has to handle two shapes |
| R7 | Add a Forge equivalent of `KeyBinding.byId` that does not scan | `CommandDispatcherForge.java:163-172` | Linear scan per press; no correctness issue seen |
| R8 | **Proposal:** bound the command queue and give socket writes a timeout | Section 4.1 of `04-interfaces-error-handling.md` | A stalled client could block the game's tick |
| R9 | Run one clean build that compiles every Yarn and Mojang name flagged as unverified | `CLAUDE.md`, "Known unverified/fragile areas"; `thor-hud-handoff.md` section 6, step 6 | The names were checked one at a time only |
| R10 | Guard the Forge map scan with the same `try` Fabric has | `MapRendererForge.java:44-137` | A scan exception reaches the tick handler |
| R11 | Correct the stale documents listed in the application spec, section 6 | `application-spec/06-limitations-glossary.md` | `CLAUDE.md`, the handoff, the client README and the port plan disagree with the code |
| R12 | **Proposal:** check whether a counted press can outlive its tick | `CommandDispatcher.java:120-126` (`tick()` clears the flag but not the counter) | An unconsumed count would fire later. Add a log line first, then read the real output (per `CLAUDE.md` section 0) |
| R13 | **Proposal:** confirm that `ItemIconRenderer`'s callback runs on the tick thread, or guard `inFlightIcons` | `ItemIconRenderer.java:74-83`; `ThorHudClient.java:389,395` | The guard depends on a thread assumption |
| R14 | Give the app a fixture of the wire format, so both halves can be tested without the game | `tools/capture_protocol.py` is the only check now | Nothing tests the wire format apart from the live harness |
| R15 | Decide the reference owner for links (`exojosh` or `venatrix-ritz`) | `fabric.mod.json:11-13`; `origin` remote | The two names both appear |
| R16 | Confirm the Forge pack format and the intended modpack target | `forge/src/main/resources/pack.mcmeta:4`; `docs/forge-1.20-port-plan.md` | Neither is checked in this tree |

## 4. Open questions the code does not answer

- Whether the app treats an `icon` with no `data` key as failure. The code sends one; the app's behaviour is unknown here.
- Whether the Forge build's container suppression default is meant to stay on. See D23.
- Whether a `hello` from adbd is ever seen by the app in practice. The code's comment describes it, and the app's
  handling is outside this tree.
- Whether `KeyMapping.click` on Forge (`CommandDispatcherForge.java:182`) counts the same way as the vanilla keyboard path. The code assumes it does.
