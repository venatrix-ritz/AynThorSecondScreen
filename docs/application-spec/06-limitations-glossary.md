# 06 - Known limitations, documentation drift, unverified claims, glossary

## 1. Known limitations (from the code)

| # | Limitation | Where |
|---|---|---|
| L1 | The HUD state and container messages are sent only on change. An app that misses a change can show stale data until the next change. There is no periodic resend. | `ThorHudClient.java:224-228`; `ContainerRelay.java:102-111` |
| L2 | The asset bundle, bindings and chat backlog go to a new client only after a player exists. A client connecting at the main menu waits for a world. | `ThorHudClient.java:142-146,321-355` |
| L3 | `ICON:minecraft:air` and `ICON:` with an empty id get no reply. The app has to time out. | `ThorHudClient.java:364` |
| L4 | Icon replies are broadcast to every connected client, not only the requester. | `HudStateServer.java:179-181,189-191` |
| L5 | Socket writes happen on the tick thread, synchronously, with no timeout set. A client that stops reading can make a write block, which would stall the game's client tick. This is a reading of the code, not a measured result. | `HudStateServer.java:296-314`; `forge/.../HudStateServer.java:201-225` |
| L6 | The incoming command queue has no size limit, and each line is handled without rate limiting. | `HudStateServer.java:47,136-141`; `ThorHudClient.java:170-192` |
| L7 | Binding display text is a snapshot. A rebind in game is not pushed to the app until it asks again with `BINDINGS`. | `KeyBindingCatalog.java:43-45` |
| L8 | Container equality ignores anything the wire format cannot express, such as custom names or NBT beyond damage. A change in those is not re-sent. | `ContainerRelay.java:34-37` |
| L9 | Chat keeps only colour. Bold, italic and obfuscated text are lost. | `ChatRelay.java:40-50` |
| L10 | Chat history is 100 messages and is lost when the mod restarts. It is cleared when the player leaves a world. | `ChatRelay.java:72,114-118` |
| L11 | Presses are dropped while a screen is open. Nothing is queued. A player who opens a screen while the app presses a key sees no effect. This is intentional. | `CommandDispatcher.java:134-143` |
| L12 | The map is 128 by 128 at one block per pixel, updated every 10 ticks, and shows the surface only. Under a ceiling it shows the layer around the player, not the whole dimension. | `MapRenderer.java:64,69,110-113` |
| L13 | Texture serving is Fabric-only. A Forge session gives the app no textures, so it has to supply its own. | `ThorHudForgeClient.java:96-97,215-230` |
| L14 | Forge item icons are flat textures. Blocks whose icon is not a single texture file may come out blank or wrong. | `ItemIconResolverForge.java:16-23` |
| L15 | Forge icon and container shapes differ from Fabric. An app that reads one must check the other. | `forge/.../HudState.java`, `ContainerRelayForge.java:32-64` |
| L16 | The damage-flash variants of each heart sprite are not served. | `HudAssetCatalog.java:53-54` |
| L17 | Forge container suppression defaults to on, so container screens are hidden on the top display until the app says otherwise. | `ContainerRelayForge.java:29,76-81` |
| L18 | `ModHudCompat` reads TACZ, LSO and ParCool by name through reflection. A renamed class or method leaves that section empty, and the failure is logged once. | `ModHudCompat.java:171-339` |
| L19 | The bind retry is every 5 seconds, forever. There is no error state beyond the chat line and the log. | `HudStateServer.java:33,114-119` |
| L20 | Legacy single-letter codes (`E`, `R`, `G`, `H`, `K`, `F`) are still accepted. The comments say to delete them once the app is updated. | `CommandDispatcher.java:101-111` |
| L21 | Only colour survives in chat, and only the RGB value. Default colour is sent as absent. | `ChatRelay.java:50-51` |
| L22 | The mod has no version negotiation beyond one integer in the hello line. | `HudStateServer.java:39-41` |

## 2. Documents that have drifted from the code

The code on `origin/master` is taken as correct for this spec. These statements in other files are out of date:

| Document | Statement | What the code shows |
|---|---|---|
| `CLAUDE.md` ("Architecture") | The HUD state is broadcast "every client tick (~20/sec, unthrottled)" | Sent on change only (`ThorHudClient.java:224-228`). The same file's "HudState" paragraph says so correctly. |
| `CLAUDE.md` ("Commands") | Smoke test shows "one JSON line per tick" | The README is right: one line per change |
| `CLAUDE.md` ("Icon request reliability") | Every request gets an answer | Air and empty ids get no answer (L3) |
| `CLAUDE.md` ("Key classes") | Lists only Fabric classes | The Forge port is in `forge/` and is not mentioned |
| `thor-hud-handoff.md` sections 1-2 | Describes an earlier protocol with a smaller `HudState` and a short command table | The current wire format is in [03-protocol-reference.md](03-protocol-reference.md). The file itself says it is a snapshot. |
| `thor-hud-handoff.md` section 3 | Lists `ItemIconRenderer.java` as "Unfinished" and the app's package names | `ItemIconRenderer` is complete and is the primary path (`CLAUDE.md`, "Offscreen icon rendering"). Package names are as in the file header. |
| `thor-hud-handoff.md` section 5 | Says `HudElementRegistry.removeElement()` is used | The code uses `replaceElement` (`GameHudVisibility.java:85-95`) |
| `src/client/java/com/exojosh/client/README.md` | "Thor HUD Bridge — starter mod": copy three files from `src/client/java/com/exojosh/thorhud/`, package `com.exojosh.thorhud` | No such package. The client code is in `com.exojosh.client`. The file is a template from before the project was set up. |
| `docs/forge-1.20-port-plan.md` | Mentions `hud_sim.py`, `thor_mc_hud.py`, a `ClientPlayerNetworkEvent` lifecycle, and a keybinding access transformer or mixin | None of those files or mechanisms exist in this tree. The Forge code uses `KeyMapping.click` and `setDown` (`CommandDispatcherForge.java:181-185`), and the build has no mixin (`forge/build.gradle`). Icon rendering and asset serving are not in the plan. |
| `README.md` (root) | Describes only the Fabric mod | The Forge port is not mentioned |
| `README.md` (root) and `fabric.mod.json:11-13` | Link to `github.com/exojosh/AynThorSecondScreen` | The `origin` remote of this checkout is `venatrix-ritz/AynThorSecondScreen`. Which owner is canonical is **unverified**. |
| `CLAUDE.md` ("Commands") | The Fabric mod is "a client-only HUD mod" | Partly true: the HUD logic is in the client source set, but `src/main` still loads on both sides (`build.gradle:18`) |

## 3. Unverified claims

Each of these is stated in the code's comments, `CLAUDE.md` or the handoff, and was not checked in this session. No
build or run was done.

- **Fabric API names and behaviour** at 0.141.5+1.21.11: `HudElementRegistry.replaceElement`, `VanillaHudElements` constants,
  `ClientReceiveMessageEvents.CHAT` and `.GAME` argument lists, `ClientTickEvents.END_CLIENT_TICK`, and the
  `CHAT` event arriving pre-decorated (`ChatRelay.java:34-37`).
- **Yarn names** flagged in `CLAUDE.md` ("Known unverified/fragile areas") and in `HudState.java:71-75`:
  `getArmor`, `getInventory().getSelectedSlot`, `ItemStack.hasGlint`, `isDamageable`, `getDamage`, `getMaxDamage`.
- **1.21.11 rendering path** in `ItemIconRenderer.java:54-72`: `ItemModelManager.clearAndUpdate`,
  `RenderSystem.outputColorTextureOverride`, `CommandEncoder.copyTextureToBuffer`. The comments say these were read from
  decompiled 1.21.11 source (`CLAUDE.md`, "Offscreen icon rendering"). That reading was not repeated here.
- **Vanilla map colouring** in `MapRenderer.java:28-35,220-246` is said to match `FilledMapItem.updateColors`. Not re-checked.
- **Forge API names**: `VanillaGuiOverlay` ids, `RenderGuiOverlayEvent.Pre`, `ClientChatReceivedEvent.System.isOverlay()`,
  `ScreenEvent.Render.Pre`, `KeyMapping.click`, `Options.keyMappings`, `Component.getString`, and `MapColor.calculateRGBColor`.
- **Reflection targets** in `ModHudCompat.java:171-339`: TACZ classes, LSO classes and capability fields, and ParCool's
  `Stamina` class. The LSO comment cites a `javap` run on `legendarysurvivaloverhaul-1.20.1-2.3.23.1.jar`, which is not
  in this tree.
- **PlayerScreenHandler slot indices** `INVENTORY_START`, `HOTBAR_START`, `EQUIPMENT_START`, `OFFHAND_ID`
  (`ContainerRelay.java:142-145`).
- **Gson omits null fields by default** (`HudStateServer.java:43`). This governs whether `data` is absent on icon failures.
- **Pack format 15** in `forge/src/main/resources/pack.mcmeta:4`.
- **Loom `1.17-SNAPSHOT`** and **ForgeGradle `6.0.+`** resolve at build time.
- **Companion app behaviour**: how it parses each message, reconnects, draws, and handles the Forge-only fields.
- **DeceasedCraft 1.20.1 modpack** as the intended Forge target (`docs/forge-1.20-port-plan.md`). The repository does not name a modpack anywhere else.
- **Hardware behaviour** of the AYN Thor: the second screen, the launcher's loading of the jar, and what happens to port
  48291 under `adb reverse`. The README describes the last one (`README.md:104`); it was not tested.

## 4. Glossary

| Term | Meaning in this repository |
|---|---|
| Accessor / Invoker | Mixin interfaces that expose a private field (`KeyBindingAccessor`) or method (`NativeImageInvoker`). |
| Asset (wire) | A texture sent as `{"type":"asset"}`, keyed by a short name such as `heart_full`. Fabric only. |
| Backlog | The last 100 chat messages, replayed to each new connection. |
| Bind retry | The accept thread tries again every 5 seconds if port 48291 is in use. |
| Bootstrap | The set of messages sent to a new connection after the hello line. |
| `BIND:<id>` | Command that presses the key binding with that translation id. |
| Binding id | A binding's translation key, such as `key.inventory`. The wire identity for a binding. |
| Bottom screen / second screen | The Thor's secondary display, where the companion app draws the HUD. |
| Cursor | The stack held on the mouse in an open container (`ScreenHandler.getCursorStack`). |
| Element key | The short name the app uses for a HUD element: `armor`, `bubbles`, `hearts`, `hunger`, `xp_bar`, `hotbar`. |
| Handler | The screen handler behind an open container (`player.currentScreenHandler`). Its `syncId` identifies it. |
| Hello | The first line on every connection: `{"type":"hello",...}`. Says what answered on the port. |
| Icon | A 64 by 64 PNG of an item, rendered from the game model on Fabric. A flat texture on Forge. |
| Latch (noplayer) | The `noplayer` message is sent once on a transition, not repeated. |
| Loopback | `127.0.0.1`. Nothing on the socket leaves the device. |
| Map tile | A 128 by 128 PNG of the terrain around the player, with its world origin and the player's position. |
| NDJSON | Newline-delimited JSON: one JSON object per line. |
| Noplayer | The message sent when the player leaves a world. |
| Presentation display | An Android display the system reports as the presentation category. The app uses one for the bottom screen. |
| Protocol version | The integer in the hello line, currently 1. |
| Shown-in-game set | The set of elements the game should draw on the top screen. Sent in full with `HUD:<keys>`. |
| Slot role | The indices `playerStart`, `hotbarStart`, `armorStart` and `offhandIndex` that tell the app where the player's inventory sits in a container. |
| Suppression (Forge) | Cancelling the top-screen rendering of container screens, so the app can show them instead. |
| SyncId | The integer identity of an open screen handler. Required in each `SLOT:` click as a staleness check. |
| Zalith Launcher 2 | The launcher the README names for running Minecraft on the Thor. |
