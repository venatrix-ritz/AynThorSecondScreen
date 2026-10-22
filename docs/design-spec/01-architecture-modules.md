# 01 - Architecture and modules

## 1. Shape of the system

One Minecraft client process contains everything that matters. The mod runs inside it as a client-side hook, and the
companion app runs in a separate Android process on the same device. The two talk only over a loopback TCP socket.
There is no shared code and no shared build (`CLAUDE.md`, "What this is").

```
Minecraft client process (Fabric or Forge)
 +--------------------------------------------------------------+
 | client tick hook --> snapshot builders --> HudStateServer ----+--> 127.0.0.1:48291 --> companion app
 |        ^                                       ^             |                         (separate process)
 |        |                                       |             |
 |  CommandDispatcher <-- command queue <---------+<------------+--- one-line commands
 |  (key presses for one tick)                                  |
 +--------------------------------------------------------------+
```

The mod has two independent builds, which share the protocol but not code:

| Build | Source root | Loader | Minecraft | Java |
|---|---|---|---|---|
| Fabric | `src/` (client set in `src/client/java/com/exojosh/client/`) | Fabric Loader 0.19.3, Fabric API | 1.21.11, Yarn | 21 |
| Forge | `forge/src/main/java/com/exojosh/forge/` | Forge 47.4.0 | 1.20.1, official mappings | 17 |

`src/main/java/com/exojosh/` is template code and is not part of the HUD (`01-purpose-scope.md` in the application spec).

## 2. Fabric module map

| Class | Responsibility | State it owns | Threads |
|---|---|---|---|
| `ThorHudClient` (`ThorHudClient.java:31`) | Client entry point and tick driver. Builds the snapshot, broadcasts changes, drains commands, starts the icon, map and bundle work | `HUD_SERVER` (`:106`), `hadPlayer`, `lastHudState`, icon queue, in-flight set, map counter, bind-warning flag | Client (render) thread |
| `HudStateServer` (`HudStateServer.java:29`) | Owns the `ServerSocket`, the client list, the command queue and the new-client queue. Serialises records to JSON | `clients`, `incomingCommands`, `newClients`, `bindFailure` | `thorhud-accept` (one), `thorhud-reader` (one per client) |
| `HudState` (`HudState.java:24`) | Immutable snapshot record of one tick, with nested `HotbarSlot` and static builders | none | Client thread |
| `GameHudVisibility` (`GameHudVisibility.java:37`) | Maps the app's element keys to vanilla HUD elements and wraps each with a per-frame check | `SHOWN_IN_GAME` set (`:79`) | Render thread reads it, tick thread writes it |
| `CommandDispatcher` (`CommandDispatcher.java:64`) | Turns a command line into a one-tick key press | `COMMANDS` map, `PENDING_RELEASE` queue | Client thread |
| `KeyBindingCatalog` (`KeyBindingCatalog.java:49`) | Snapshot of all key bindings, with display strings translated | none | Client thread |
| `ChatRelay` (`ChatRelay.java:61`) | Receives chat via Fabric events, keeps the backlog, sends chat | `HISTORY` deque (`:77`), `server` reference | Client thread (event callbacks run there) |
| `ContainerRelay` (`ContainerRelay.java:47`) | Captures the open screen handler, sends it on change, performs slot clicks | `lastSent` cache (`:59`) | Client thread |
| `HudAssetCatalog` (`HudAssetCatalog.java:38`) | Maps short keys to vanilla texture paths and reads PNG bytes on request | `ASSETS` map (`:40`) | Client thread |
| `ItemIconRenderer` (`ItemIconRenderer.java:84`) | Offscreen render of an item to a PNG, with an async GPU readback | Static GPU textures, projection, buffer allocator, vertex consumers (`:110-116`) | Client thread; callback on the render thread |
| `ItemIconResolver` (`ItemIconResolver.java:41`) | Fallback: reads an item's raw texture file | none | Client thread |
| `MapRenderer` (`MapRenderer.java:61`) | Renders the 128 by 128 terrain tile and encodes it | none | Client thread |
| `NativeImageInvoker` (`mixin/NativeImageInvoker.java:19`) | Exposes `NativeImage.write(WritableByteChannel)` so PNGs are encoded in memory | none | n/a |
| `KeyBindingAccessor` (`mixin/KeyBindingAccessor.java:35`) | Exposes `KeyBinding.timesPressed` | none | n/a |
| `Aynthor_secondscreen_v1_21Client` (`:5`) | Empty client entry point | none | n/a |

### Dependency directions

- `ThorHudClient` depends on every other HUD class. It is the only class that calls the others on the tick.
- `HudStateServer` depends on the record types `ChatRelay.Segment`, `ContainerRelay.ScreenHandlerState`,
  `MapRenderer.Tile` and `KeyBindingCatalog.Entry` (`HudStateServer.java:228,255,266,212`). Those classes call back into
  `HudStateServer`: `ChatRelay.install(HudStateServer)` (`ChatRelay.java:89`) and `ContainerRelay.broadcastIfChanged(...,
  HudStateServer)` (`ContainerRelay.java:102`). The dependency is two-way, through the data records. This is a design observation.
- `ItemIconRenderer` and `ItemIconResolver` are used only from `ThorHudClient.renderAndSendIcon` (`ThorHudClient.java:394-408`).
- `KeyBindingCatalog` is used by `ThorHudClient` for the request path and by `pushBundleToNewClients`
  (`ThorHudClient.java:178,327`).

### Global state

Every HUD class holds static state. `ThorHudClient.HUD_SERVER` is `public static final` (`ThorHudClient.java:106`) and
`CommandDispatcher`, `GameHudVisibility`, `ChatRelay` and `ContainerRelay` are static utilities. The code assumes one
client instance per process, which is also what Minecraft provides. Nothing in the code tests or prevents a second
instance.

## 3. Forge module map

| Class | Responsibility | State it owns | Threads |
|---|---|---|---|
| `AynThorSecondScreenForge` (`:12`) | Mod entry. Inside `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` registers four listener classes on `MinecraftForge.EVENT_BUS` and calls `ThorHudForgeClient.init()` (`:16-24`) | none | Mod loading |
| `ThorHudForgeClient` (`:18`) | Tick listener (`onClientTick`, `:45`). Same job as the Fabric driver, plus the Forge-only command set | `HUD_SERVER` (`:35`), icon queue, in-flight set, `lastHudState`, `hadPlayer` | Client thread |
| `HudStateServer` (`:25`) | Socket server. Same shape as the Fabric one, with `broadcastRaw` unsynchronised | `clients`, `incomingCommands`, `newClients`, `bindFailure` | `ThorHud-Accept` (one), `ThorHud-Reader-<port>` (per client) |
| `HudState` (`:17`) | Snapshot record, adds `armorToughness`, `saturation`, `exhaustion`, `armorSlots`, `effects`, `gun`, `survival`, `target` | none | Client thread |
| `ModHudCompat` (`:36`) | Reads Forge's player and crosshair state, and TACZ, LSO and ParCool by reflection | reflection caches (`:86-111`) | Client thread |
| `GameHudVisibilityForge` (`:17`) | Cancels the vanilla overlay for elements that are not shown in game | `SHOWN_IN_GAME` (`:43`) | Render thread, via `RenderGuiOverlayEvent.Pre` |
| `ChatRelayForge` (`:22`) | Receives `ClientChatReceivedEvent`, backlog, chat sending | `HISTORY`, `server` | Client thread |
| `ContainerRelayForge` (`:24`) | Render suppression for container screens, handler capture, slot clicks, widget presses, sort and screen click | `suppressRendering` (`:29`), `lastSent` (`:30`) | Render thread (suppression); client thread (rest) |
| `CommandDispatcherForge` (`:14`) | Command routing and one-tick press via `KeyMapping.click` and `setDown` | `COMMANDS`, `PENDING_RELEASE`, `initialized` | Client thread |
| `KeyBindingCatalogForge` (`:14`) | Snapshot of `keyMappings` | none | Client thread |
| `ItemIconResolverForge` (`:14`) | Reads the raw texture file | none | Client thread |
| `MapRendererForge` (`:26`) | Renders the map tile with `BufferedImage` and `ImageIO` | none | Client thread |

Forge has no `ItemIconRenderer`, no `HudAssetCatalog` and no mixin. It hides overlays by cancelling
`RenderGuiOverlayEvent.Pre` (`GameHudVisibilityForge.java:45-60`), where Fabric replaces the element. The Forge side
speaks the same protocol with its own scope (`docs/forge-1.20-port-plan.md` plans the port; `forge/build.gradle`
declares no mixin).

## 4. Threads

| Thread | Created by | Runs | Blocks on |
|---|---|---|---|
| Client (render) thread | Minecraft | Tick hooks, event callbacks (chat, overlay, container render), GPU readback callbacks (Fabric icon path) | Nothing in the HUD code, apart from socket writes (see `04-interfaces-error-handling.md`) |
| `thorhud-accept` / `ThorHud-Accept` | `HudStateServer.start` (`HudStateServer.java:55-59`; Forge `:43-47`) | Bind, hello, register clients, bind-retry loop | `accept()`; `Thread.sleep(5000)` on failure |
| `thorhud-reader` / `ThorHud-Reader-<port>` | Accept thread, one per client | `readLine()` loop, pushes trimmed lines to `incomingCommands` | `readLine()` |

The reader threads never touch game state. They only enqueue text. Everything that touches game state runs on the
client thread, from the tick hook or from an event callback. This keeps the tick the only place where commands take effect.

The Fabric icon path runs `renderBase64Png`'s callback on the render thread after the GPU fence signals
(`ItemIconRenderer.java:78-82`). Forge's icon path is synchronous and has no callback.

## 5. Files that run on which side

| File | Side | Notes |
|---|---|---|
| `src/client/java/com/exojosh/client/*.java` | Client only | Compiled in the client source set (`build.gradle:18`) |
| `src/main/java/com/exojosh/*.java` | Both | Template only |
| `forge/src/main/java/com/exojosh/forge/*.java` | Client only at runtime | Registered only on `Dist.CLIENT` (`AynThorSecondScreenForge.java:17-23`). `ModHudCompat` and the Forge classes are plain classes, so they are present on a server but never loaded there |
| `tools/*.py` | Developer machine | Not part of the mod |
