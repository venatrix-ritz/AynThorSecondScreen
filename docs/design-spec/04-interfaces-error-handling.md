# 04 - Interfaces, error handling and device constraints

## 1. Internal interfaces (Fabric)

Signatures are as written in the source. Each entry names the file and the line of the declaration.

### 1.1 Socket server, `HudStateServer` (`HudStateServer.java`)

| Method | Line | Contract |
|---|---|---|
| `HudStateServer(int port)` | `:51` | Stores the port. Does not bind. |
| `void start()` | `:55` | Starts the accept thread as a daemon. Not guarded: a second call would start a second accept loop, and both would try to bind. |
| `String bindFailure()` | `:129` | Null when the port is held. Read from the tick thread. |
| `String pollCommand()` | `:155` | Oldest queued command line, or null. Any thread. |
| `Socket pollNewClient()` | `:164` | A client connected since the last call, or null. Tick thread only in practice. |
| `void broadcast(HudState)` | `:168` | Sends one JSON line to every client. |
| `void sendStateTo(Socket, HudState)` | `:175` | Sends one HUD line to one client. |
| `void broadcastIcon(String, String)` | `:179` | Icon with data, to all. |
| `void broadcastIconFailure(String)` | `:189` | Icon without data, to all. |
| `void broadcastAsset(String, String)` / `sendAssetTo(Socket, ...)` | `:194,200` | Asset to all or one. A null data value is written as an absent field. |
| `void broadcastBindings(List<Entry>)` / `sendBindingsTo(...)` | `:212,218` | Binding list to all or one. |
| `void broadcastChat(List<Segment>)` / `sendChatTo(...)` | `:228,234` | Chat to all or one. |
| `void broadcastNoPlayer()` | `:245` | `noplayer` to all. |
| `void broadcastContainer(...)` / `sendContainerTo(...)` | `:255,261` | Container state to all or one. |
| `void broadcastMap(MapRenderer.Tile)` | `:266` | Map tile to all. |
| `boolean hasClients()` | `:285` | True when the broadcast list is not empty. Used to skip work. |

Record types on the wire are public nested records (`:317-368`): `IconResponse`, `BindingsResponse`, `ChatResponse`,
`NoPlayerResponse`, `HelloResponse`, `AssetResponse`, `MapResponse`. The HUD and container records are their own types.

### 1.2 Tick driver, `ThorHudClient`

| Method | Line | Contract |
|---|---|---|
| `onInitializeClient()` | `:119` | Installs visibility wrappers, installs chat hooks, starts the server, registers the tick. Called by Fabric once. |
| `onClientTick(MinecraftClient)` | `:136` | Private. Runs once per END tick. |
| `broadcastHudStateIfChanged(HudState)` | `:224` | Private. Sends when `!state.equals(lastHudState)`. |
| `pushBundleToNewClients()` | `:321` | Private. Must run on the client thread (its comment says resource manager and language state are involved). |

### 1.3 Key presses, `CommandDispatcher`

| Method | Line | Contract |
|---|---|---|
| `static void tick()` | `:120` | Releases every binding pressed on the previous tick. Call once per END tick, before `dispatch` in that tick. |
| `static void dispatch(String code)` | `:129` | Routes one command line. Drops the command, with a log line, when a screen is open. Unknown ids and codes are logged and dropped. |

### 1.4 Other Fabric classes

| Class | Entry points | Contract |
|---|---|---|
| `GameHudVisibility` | `install()` (`:85`), `setShownInGame(String)` (`:108`) | `install` runs once at init. `setShownInGame` takes the whole set; it is idempotent. |
| `ChatRelay` | `install(HudStateServer)` (`:89`), `history()` (`:103`), `clearHistory()` (`:114`), `send(MinecraftClient, String)` (`:131`) | `install` must run before the server starts so no message is missed (`ThorHudClient.java:126-131`). `history()` returns a copy. |
| `ContainerRelay` | `invalidate()` (`:97`), `broadcastIfChanged(MinecraftClient, HudStateServer)` (`:102`), `current()` (`:114`), `click(MinecraftClient, String)` (`:179`) | `broadcastIfChanged` does nothing without a player. `click` does nothing without a player or interaction manager. |
| `KeyBindingCatalog` | `snapshot()` (`:73`) | Returns an empty list when options are not ready. |
| `HudAssetCatalog` | `keys()` (`:122`), `resolveBase64Png(String)` (`:131`) | Returns empty for an unknown key or a missing resource. |
| `ItemIconRenderer` | `renderBase64Png(ItemStack, Consumer<String>)` (`:130`) | The callback is called **exactly once**: with null immediately when the model is empty or the render throws, or later on the render thread with the PNG or null (`:124-129` comment). |
| `ItemIconResolver` | `resolveBase64Png(String)` (`:43`) | Raw texture read. Returns empty when neither path resolves. |
| `MapRenderer` | `render(MinecraftClient)` (`:98`) | Must run on the client thread (`:95-97`). Returns empty on any failure. |

### 1.5 Mixin interfaces

| Interface | Target | Members |
|---|---|---|
| `KeyBindingAccessor` (`mixin/KeyBindingAccessor.java:36-43`) | `KeyBinding` | `@Accessor("timesPressed")` getter and setter |
| `NativeImageInvoker` (`mixin/NativeImageInvoker.java:19-24`) | `NativeImage` | `@Invoker("write")` taking `WritableByteChannel` |
| `ExampleClientMixin` (`mixin/ExampleClientMixin.java:9-15`) | `MinecraftClient.run` | Empty injection. Template. |

## 2. Forge interfaces

The Forge classes mirror the Fabric ones with these differences:

| Class | Entry points | Difference |
|---|---|---|
| `ThorHudForgeClient` | `init()` (`:39`), `onClientTick(TickEvent.ClientTickEvent)` (`:46`) | Event-bus listener. Static. |
| `HudStateServer` | `start()` (`:43`), `bindFailure()`, `pollNewClient()`, `pollCommand()`, `broadcast*`, `send*` | No asset methods are called (`broadcastAsset` and `sendAssetTo` exist, `:83-91`, but nothing calls them). |
| `ContainerRelayForge` | `onScreenRenderPre(ScreenEvent.Render.Pre)` (`:77`), `click(Minecraft, String)` (`:169`), `clickWidget`, `clickScreen`, `sortInventory`, `setSuppressRendering` (`:68`) | Render-thread listener for suppression. The rest runs from the tick. |
| `GameHudVisibilityForge` | `onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre)` (`:46`), `setShownInGame(String)` (`:62`) | Cancels overlays. Does not replace them. |
| `ChatRelayForge` | `setServer`, `onClientChatReceived(ClientChatReceivedEvent)` (`:37`), `send`, `history`, `clearHistory` | Not split into CHAT and GAME. |
| `CommandDispatcherForge` | `tick()` (`:89`), `dispatch(String)` (`:97`) | Same contract as Fabric's dispatcher. |
| `ItemIconResolverForge` | `resolveBase64Png(String)` (`:16`) | The only icon path. |
| `MapRendererForge` | `render(Minecraft)` (`:44`) | Returns empty on encode failure. |
| `ModHudCompat` | `getGunHud`, `getSurvivalHud`, `getActiveEffects`, `getArmorSlots`, `getTargetHud` | `getGunHud` and `getSurvivalHud` return a default value on failure. `getActiveEffects` and `getArmorSlots` have no catch, and `getTargetHud` has none either. |

## 3. Wire contract

The wire contract is described in [../application-spec/03-protocol-reference.md](../application-spec/03-protocol-reference.md).
The design-level rules are these:

- Every message is one JSON object on one line, with a `type` field except the HUD snapshot, which has none.
- The app must treat unknown fields as ignorable. On the records compared here (HUD snapshot and container state), Forge's
  messages contain every Fabric field and more, so an app written for Fabric's shape reads Forge's fine if it ignores extras.
- `noplayer` is a latch. The app keeps "no player" until a HUD snapshot arrives. A snapshot does not reset the latch on the mod side; it is the app that must do it.
- Commands carry no id. Replies to icon requests are matched by `itemId`, and an icon reply is sent to every client, so the app must be able to ignore an icon it did not ask for.

## 4. Error handling

Each row gives the condition, what the code does, and what the user sees. "User" means the player on the top screen,
unless the row says otherwise.

| Condition | Code path | What happens | User sees |
|---|---|---|---|
| Port 48291 held by something else | `acceptLoop` catches `IOException` (`HudStateServer.java:103-112`) | Sets `bindFailure`, writes to stderr, waits 5 s, retries | Fabric: one red chat line per failure (`ThorHudClient.java:244-259`). Forge: one chat line, different text (`ThorHudForgeClient.java:167-180`) |
| `accept()` throws | Same catch | Same retry; the server socket is closed by try-with-resources | Same as above |
| Write to a client fails | `writeLine` / `sendTo` / `broadcastRaw` catches `IOException` (`HudStateServer.java:307-313`) | Client removed from the list and closed | Nothing. The app sees the connection drop |
| Client closes or reader EOF | `readLoop` `finally` (`:145-151`); Forge `clientReadLoop` `finally` (`:195-198`) | Client removed and closed | Nothing |
| Icon: empty id or `minecraft:air` | `queueIconRequest` returns early (`ThorHudClient.java:364`) | No reply | Nothing. The app times out (see [02-algorithms-timing.md](02-algorithms-timing.md)) |
| Icon: unknown id | `Identifier.tryParse` returns null (`:418-419`) | Failure reply | Nothing |
| Icon: render produces nothing | `renderBase64Png` calls back with null (`ItemIconRenderer.java:137-140`) | Falls back to `ItemIconResolver` | Icon from the texture file |
| Icon: render throws | `catch RuntimeException` in `renderBase64Png` and in the readback (`:146-150,282-288`) | Logs with stack trace, callback with null | Fallback as above |
| Icon: nothing resolves | `broadcastIconFailure` (`ThorHudClient.java:406`) | Reply without data | App decides |
| Asset missing from the resource stack | `HudAssetCatalog.resolveBase64Png` returns empty (`:136-141`) | Asset sent with no data | App's own fallback (unverified) |
| Asset read throws | `catch IOException or RuntimeException` (`HudAssetCatalog.java:146-149`) | Logs; empty result | As above |
| Map render throws (Fabric) | `catch Exception` around the whole method (`MapRenderer.java:214-217`) | Logs; no tile | Map does not update |
| Map encode throws (Forge) | `catch Exception` around encoding (`MapRendererForge.java:153-156`) | Logs; no tile | Map does not update |
| Map scan throws (Forge) | Not caught. The scan is outside the `try` (`MapRendererForge.java:44-137`) | Propagates out of the tick handler. How Forge then logs it is **unverified** | Unknown |
| Unknown command line | `CommandDispatcher.dispatch` logs and returns (`:167-171`) | Dropped | Nothing |
| `BIND:` id not found | Logs and returns (`:157-162`) | Dropped | Nothing |
| Command while a screen is open | Logs and returns (`:140-143`) | Dropped | Nothing |
| Slot click: malformed | Logs (`ContainerRelay.java:184-187`) | Dropped | Nothing |
| Slot click: `ACTION` not a constant | `IllegalArgumentException` caught (`:198-201`) | Dropped | Nothing |
| Slot click: stale `syncId` | Logs (`:204-208`) | Dropped before any change | Nothing; the app's next container state shows the true state |
| Slot click: `slotId` out of range | Logs (`:210-213`) | Dropped | Nothing |
| HUD: unknown element key (Fabric) | Logs (`GameHudVisibility.java:116-119`) | Ignored | Nothing |
| HUD: unknown element key (Forge) | Skipped silently (`GameHudVisibilityForge.java:70-72`) | Ignored | Nothing |
| Container handler `getType()` throws | Caught for `UnsupportedOperationException` (`ContainerRelay.java:158-164`) | `handlerType` is null | Generic layout (app side, unverified) |
| Forge reflection target missing | `catch Throwable` in `init*Reflection`; methods stay null (`ModHudCompat.java:171-196,288-339`) | Feature inactive; one log line | Missing section in the HUD |
| Forge reflection call throws | `catch Exception` per call (`ModHudCompat.java:166-168,230,253`) | Feature reports inactive | Missing section |
| Chat send with no player | Returns early (`ChatRelay.java:132`) | Nothing sent | Nothing |
| Container click with no player or interaction manager | Returns early (`ContainerRelay.java:181`) | Nothing done | Nothing |
| Interrupt during bind sleep | `InterruptedException` ends the accept thread (`HudStateServer.java:114-119`) | Thread exits; no restart | Server dead until the game restarts |

### 4.1 Things the error handling does not cover

- **No write timeout.** A client that stops reading fills the kernel buffer, and the next write on the tick thread blocks.
  The code gives no timeout for this (`HudStateServer.java:296-314`). This is a reading of the code. It was not measured.
- **No cap on the command queue.** `incomingCommands` is unbounded. A client that sends faster than the tick drains it
  will grow the queue without limit (`HudStateServer.java:47,136-141`).
- **Icon replies are not deduplicated per client.** A reply goes to every client. That is harmless with one app.
- **Static state after a world change.** The icon renderer's GPU objects are static and reused. If a render is in flight
  when the world changes, the callback still runs. Nothing in the code cancels it. This is an inference from the code.
- **No retry for the `ICON:` air case.** See the table above.
- **No logging framework.** The HUD classes write to `System.out` and `System.err` (for example `ThorHudClient.java:287`).
  The main class uses an SLF4J logger (`src/main/java/com/exojosh/Aynthor_secondscreen_v1_21.java:16`) but the HUD code
  does not use it.

## 5. Device and OS constraints

| Constraint | Consequence in the design | Where it shows |
|---|---|---|
| Game and app run in separate Android app sandboxes, with different UIDs | No shared memory; loopback TCP is the channel | `CLAUDE.md` ("Why a socket, not shared memory"); `thor-hud-handoff.md` section 1 |
| The socket is fixed at 48291 with no configuration | A conflict has no setting to change. The retry loop and chat warning are the only handling | `HudStateServer.java:33,104-112`; `ThorHudClient.java:244-259` |
| `adb reverse` can hold the same port | The hello line identifies the mod. Clients that connect to adbd see a hello that is not the mod's | `HudStateServer.java:35-37,87-90` |
| Rendering must happen on the render thread with no render pass open | Icons are rendered from the tick, not from a HUD callback | `ItemIconRenderer.java:74-83` comment; `ThorHudClient.java:195` |
| The resource manager is client-thread state | Assets and bindings are pushed on the tick, not on the accept thread | `ThorHudClient.java:311-320`; `HudStateServer.java:93-96` |
| Battery on a handheld | Snapshots and containers are sent on change, and map tiles only while a client is connected | `ThorHudClient.java:206-223,297-300` |
| Launcher loads the mod from a jar the user imports | Exactly one jar is produced, no sources jar | `build.gradle:72-80` comment |
| The second screen is a presentation display, found by the app | The mod does not know whether a second display exists | `README.md:44-45`; app side, unverified |
| Textures are not redistributed | The mod reads the player's own installation at runtime and sends the bytes on the socket | `README.md:135-137`; `HudAssetCatalog.java:17-24` |
| Java and Minecraft versions are fixed | Fabric needs 21 and 1.21.11; Forge needs 17 and 1.20.1 | `build.gradle:68-70`; `forge/build.gradle:19`; `gradle.properties:10` |
| Forge's client code must not load on a dedicated server | Listeners are registered only inside `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` | `AynThorSecondScreenForge.java:16-23` |

## 6. Concurrency rules as written

| Shared data | Writers | Readers | Guard |
|---|---|---|---|
| `incomingCommands` (`HudStateServer`) | Reader threads | Tick thread | `ConcurrentLinkedQueue` |
| `newClients` | Accept thread | Tick thread | `ConcurrentLinkedQueue` |
| `clients` | Accept thread, reader threads (remove), tick thread (broadcast) | All | `CopyOnWriteArrayList` |
| `bindFailure` | Accept thread | Tick thread | `volatile` |
| Each client socket's output | Accept thread (hello), tick thread | Same | `synchronized (client)` in `writeLine` on Fabric; none on Forge |
| `ChatRelay.HISTORY` | Event callbacks (client thread) | Tick thread | `synchronized (HISTORY)` |
| `GameHudVisibility.SHOWN_IN_GAME` | Tick thread | Render thread | `ConcurrentHashMap.newKeySet()` |
| `ContainerRelay.lastSent` | Tick thread | Tick thread | none; every access in the code is from the tick path |
| `ChatRelay.server` | Init | Event callbacks | none; written once |
| `ItemIconRenderer` GPU objects | Tick thread | Render thread callback | none; the comment argues GL ordering makes this safe |
| `inFlightIcons` (`HashSet`) | Tick thread; the Fabric readback callback removes ids | Tick thread | none. Safe only if the callback runs on the tick thread. `ItemIconRenderer.java:74-83` says the tick and the render callbacks run on the render thread, which is the client thread in vanilla (**unverified** here) |
| `SHOWN_IN_GAME` on Forge | Tick thread | Render thread | `ConcurrentHashMap.newKeySet()` |

The `inFlightIcons` and `ItemIconRenderer` rows rest on a thread assumption that the code states but does not check.
See [05-design-decisions-roadmap.md](05-design-decisions-roadmap.md) (item R13).
