# 02 - Algorithms and timing

Each section gives the algorithm as the code runs it, the constants with their lines, and the timing that follows
from them. Timing in game ticks is exact from the code. Timing in seconds assumes Minecraft's standard client tick
rate of 20 per second; that rate is vanilla behaviour and was **not measured** here.

## 1. The client tick loop (Fabric)

`ThorHudClient.onClientTick` (`ThorHudClient.java:136-204`) runs once per client tick. The steps run in this order:

1. `CommandDispatcher.tick()` releases the key presses from the previous tick (`:139`, `CommandDispatcher.java:120-126`).
2. If there is no player, call `onLeftWorld()` and return. This is the only early exit (`:141-145`).
3. Set `hadPlayer = true` and run the bind-failure check (`:146-147`).
4. Build the `HudState` from the player (`:149-165`). Every field is read on every tick. The hotbar and off-hand are
   rebuilt from the inventory each time (`HudState.java:107-122`).
5. Broadcast the snapshot if it differs from the last one (`:167`, `:224-228`).
6. Drain the command queue. Each line is routed by prefix or exact match (`:169-192`). Icon requests are queued, not
   rendered, so they cost nothing here.
7. If clients are connected, prepare the bootstrap set for newcomers (`:194`, `:321-355`).
8. Render up to 2 queued icons (`:195`, `:370-376`).
9. Possibly render and send a map tile (`:196`, `:297-309`).
10. If clients are connected, rebuild the container state and send it if it changed (`:201-203`).

The order is deliberate. A snapshot is sent before commands are handled, so the effect of a command reaches the app on
the next tick at the earliest. The container check is last, so a click handled in step 6 is visible in step 10 of the same tick
(`ContainerRelay.java:221` clears the cache so the change is sent even if nothing else differs).

The Forge loop (`ThorHudForgeClient.java:45-147`) has the same order, with `CommandDispatcherForge.tick()` first (`:49`).
It returns early when the phase is not `END` (`:47`) and when there is no player (`:53-56`).

## 2. Change detection

Snapshot and container messages are sent when the new value differs from the last value sent. The comparison is
structural equality on the record, which includes the lists (`HudState` and `ContainerRelay.ScreenHandlerState` are
records). Consequences:

- A field that the wire format cannot express does not trigger a send. Two stacks that differ only in custom name or
  NBT beyond damage are equal (`ContainerRelay.java:34-37`).
- A change in `absorption`, `air`, `hardcore`, or any other field triggers a full HUD line, because the whole record is sent.
- The cache is reset in these cases, so the next tick sends:
  - when the player leaves a world (`lastHudState = null`, `ThorHudClient.java:280`; Forge `:187`);
  - after a slot click (`lastSent = null`, `ContainerRelay.java:221`; Forge `ContainerRelayForge.java:207`);
  - when the world is left (`ContainerRelay.invalidate`, `ContainerRelay.java:97-99`, called from `ThorHudClient.java:283`).
    Closing a container needs no reset: the handler changes, so the next capture differs.
- A newly connected client is given the cached state, not a fresh one (`ThorHudClient.java:348-350`). If nothing is
  cached yet, the client receives nothing for that message type until a change.

Cost per tick (Fabric), with clients connected: one `HudState` and one `ScreenHandlerState` are built each tick, which
means about 46 slot records for a player inventory and more for a large container. These are small allocations
(`ContainerRelay.java:28-32` class comment). With no client connected, `ContainerRelay.broadcastIfChanged` is skipped
(`ThorHudClient.java:201`), but `HudState` is still built every tick (`:149-165`). That is not gated by `hasClients()`
on Fabric. The Forge code is the same (`ThorHudForgeClient.java:64-90`).

## 3. Timing constants

| Quantity | Value | Where | Effective timing |
|---|---|---|---|
| Client tick | vanilla, one per game tick | Fabric `ClientTickEvents.END_CLIENT_TICK` (`ThorHudClient.java:133`); Forge `TickEvent.ClientTickEvent` END (`ThorHudForgeClient.java:46-47`) | Nominal 50 ms at 20 TPS (**unverified** for this build) |
| Bind retry | `BIND_RETRY_MS = 5000` | `HudStateServer.java:33`; `forge/.../HudStateServer.java:27` | Every 5 s while the port is taken or accept fails |
| Accept backlog | 4 (Fabric), 50 (Forge) | `HudStateServer.java:78`; `forge/.../HudStateServer.java:146` | Only for pending connections not yet accepted |
| Icon renders per tick | `MAX_ICON_RENDERS_PER_TICK = 2` | `ThorHudClient.java:74`; Forge `ThorHudForgeClient.java:27` | At most 2 per tick, so 40 per second at 20 TPS |
| Map tile interval | `MAP_UPDATE_INTERVAL_TICKS = 10` | `ThorHudClient.java:81`; Forge `ThorHudForgeClient.java:28` | 10 ticks, so 2 per second at 20 TPS. The code comment says "~2/sec" |
| Chat backlog | `HISTORY_LIMIT = 100` | `ChatRelay.java:72`; `ChatRelayForge.java:24` | 100 messages, oldest dropped first |
| Key press | one tick | `CommandDispatcher.java:182-191` | Set on dispatch, released at the start of the next END hook |
| HUD snapshot | on change | `ThorHudClient.java:224-228` | Event-driven, checked every tick |
| Container state | on change | `ContainerRelay.java:102-111` | Event-driven, checked every tick while clients are connected |
| Socket read | no timeout | `HudStateServer.java:133-152` | Blocks until a line or EOF |
| Socket write | no timeout set | `HudStateServer.java:296-314` | Blocks until the kernel buffer accepts the bytes |

## 4. Bind and accept algorithm

`HudStateServer.acceptLoop` (`HudStateServer.java:76-121`) is one loop that owns both the bind and the accept:

```
loop:
  try (ServerSocket s = new ServerSocket(48291, backlog=4, 127.0.0.1)):
      serverSocket = s
      bindFailure = null
      print "Listening"
      while not s.isClosed():
          client = s.accept()                      # any IOException here is handled below
          write hello line to client               # before the client is registered
          clients.add(client)
          newClients.add(client)
          start reader thread for client
  catch IOException e:
      bindFailure = e.message
      print to stderr, with the adb reverse hint
  sleep 5000 ms                                    # InterruptedException ends the thread
```

Consequences:

- The retry delay applies to a failed bind and to any `IOException` from `accept()`. Both lead to the same 5-second
  wait and a new bind. An accept error is therefore recovered the same way as a port conflict.
- `bindFailure` is set on failure and cleared on success. The tick loop reads it once per tick and posts one chat
  line per failure (`ThorHudClient.java:244-259`). The flag `warnedAboutBindFailure` resets when the bind succeeds
  (`:247`), so a recurring conflict is reported again.
- Forge's version (`forge/.../HudStateServer.java:143-177`) creates the `ServerSocket` at the top of each outer
  iteration, and its catch closes the socket explicitly (`:167`) before the 5-second sleep. The retry timing is the same.

### Why the loop exists

`README.md:104` and `CLAUDE.md` ("Architecture") describe the failure this loop is written for: a leftover
`adb reverse tcp:48291 tcp:48291` makes adbd hold the port. adbd accepts a connection and drops it at once. The hello
line lets the app tell "this is the mod" from "this is something else" (`HudStateServer.java:35-37,87-90`). How the app
reacts to that case is **unverified** here.

## 5. Connection handoff

On accept (`HudStateServer.java:84-101`):

1. The client is accepted on the accept thread.
2. The hello line is written on the accept thread, before the socket is registered (`:90`). It is the only write that
   thread makes, so it cannot interleave with a broadcast.
3. The socket is added to `clients` (`:92`). From here on, the tick thread's broadcasts include it.
4. The socket is added to `newClients` (`:97`). The tick thread picks it up on its next pass, with the player check first.
5. A reader thread is started (`:99-101`).

Because step 3 precedes step 4, a broadcast from the tick thread can reach the newcomer before its bootstrap. For
example, if the HUD changed in the same tick, the newcomer gets a HUD line, then assets, then bindings and chat. The
bootstrap also resends the last snapshot (`ThorHudClient.java:348-350`), so the newcomer may see the same snapshot twice.
Both orderings are valid for a client that handles each line on its own. This is an inference from the code, not
an observation of the app.

## 6. Icon request algorithm

Fabric:

```
on "ICON:<id>":
  if id is empty or "minecraft:air": ignore (no reply)                  # ThorHudClient.java:364
  if inFlightIcons.add(id): iconRequestQueue.add(id)                    # dedupe while in flight

each tick, up to 2 times:
  id = queue.poll()
  stack = player's first matching stack, else registry default stack   # resolveStack :417-432
  if stack missing or empty: reply failure; drop in-flight; continue
  ItemIconRenderer.renderBase64Png(stack, callback)
     - empty render state: callback(null) immediately
     - otherwise draw to a 64x64 texture, read back asynchronously
  callback(base64):
     remove id from in-flight
     if base64 != null: broadcast icon
     else: ItemIconResolver(id); broadcast icon, or failure if that is also empty
```

- Each request gets exactly one final reply, unless it is one of the ignored ids above.
- `inFlightIcons` is a set. A request for an id that is already queued or rendering is not added again. It is removed
  when the reply is sent (`ThorHudClient.java:389,395`), so a later request for the same id is served again.
- The renderer's callback is invoked on the render thread after the GPU fence (`ItemIconRenderer.java:278-289`).
  Until then, the id stays in flight.

Forge:

```
each tick, up to 2 times:
  id = queue.poll()
  base64 = texture file for id, from the resource manager (item/ then block/)
  broadcast icon, or failure
  remove id from in-flight
```

`ThorHudForgeClient.java:200-213`. There is no render step on Forge, so the queue drains in the same tick it is filled
in the typical case. The player's own stack is not used.

## 7. Map tile algorithm

`MapRenderer.render` (`MapRenderer.java:98-218`). Coordinates: the tile is centred on the player's block column, so
`originX = floor(x) - 64` and `originZ = floor(z) - 64` (`:105-108`). Each column in the tile is scanned once.

For each column `px` in 0 to 127, the scan starts at row `pz = -1` and runs to 127 (`:124-133`). Row -1 is not written
to the image. It supplies the height neighbour for row 0, so row 0 is not shaded against zero (`:127-131`).

Per cell:

1. Find the chunk. The chunk is cached and only re-looked-up when the 16-block section changes (`:136-142`).
2. If the chunk is missing or empty, write a transparent pixel and reset `previousHeight` to 0 (`:144-152`).
3. Choose the start height:
   - Surface dimension: `WORLD_SURFACE` heightmap value plus 1 (`:154-156`).
   - Ceiling dimension: the player's block row plus 1, clamped to the world (`:112-113,154`).
4. Walk down while the block's map colour is `CLEAR` (`:169-172`). The walk stops at the bottom of the world.
5. If the block is a fluid, walk further down to measure its depth (`:174-186`). Then use the fluid's own state if the
   block above it is not a solid top face (`:248-254`).
6. Shade:
   - Water (`WATER_BLUE` on Fabric, `WATER` on Forge): `shade = depth * 0.1 + checker * 0.2`. HIGH below 0.5, LOW above 0.9.
   - Everything else: `shade = (height - previousHeight) * 4 / (1 + 4) + (checker - 0.5) * 0.4`. HIGH above 0.6, LOW below -0.6.
   - `checker = floorMod(worldX + worldZ, 2)`. It uses world coordinates, so the pattern does not move as the player walks
     (`:228-231`).
7. Write the colour for `MapColor` at that brightness (`:198`). Forge uses `calculateRGBColor` and writes ARGB with alpha `0xFF`.
8. Encode the image to PNG and base64 it. Return a `Tile` with the origin, the player's exact position and the yaw (`:205-213`).

The shading thresholds are copied from vanilla's `FilledMapItem` per the file header (`MapRenderer.java:28-35`).
That claim is **unverified** here.

Cost: 128 columns by 129 rows (one priming row per column), so about 16,500 cells per tile, each with one or more block-state reads. The tile is rendered only when a client is
connected, at most every 10 ticks (`ThorHudClient.java:297-309`).

Forge's version (`MapRendererForge.java:44-157`) is the same algorithm. It has one difference in robustness: the
pixel scan is not inside a `try`. Only encoding is (`:139-156`). A scan exception therefore propagates out of the tick
handler on Forge. On Fabric the whole method is in a `try` that catches `Exception` (`MapRenderer.java:214-217`).

## 8. Command and key-press lifecycle (Fabric)

```
tick N, END hook:
  CommandDispatcher.tick()        releases presses from tick N-1: setPressed(false)
  ...
  command "5" arrives -> dispatch:
     if client.currentScreen != null: drop and log
     else pressForOneTick(options.hotbarKeys[4]):
        timesPressed += 1           (KeyBindingAccessor)
        setPressed(true)            (isPressed() reads this)
        PENDING_RELEASE.add(binding)

tick N+1, vanilla:
  handleInputEvents() runs only with no screen or overlay open
     wasPressed() drains timesPressed; isPressed() reads the flag

tick N+1, END hook:
  CommandDispatcher.tick()        releases: setPressed(false)
```

Points that follow from the code:

- A press is one game tick long at the vanilla level (`CommandDispatcher.java:41-63,182-191`).
- `tick()` clears only the held flag (`:124`). It does not reset `timesPressed`. If a press was counted but vanilla did
  not drain it, the count stays and fires later. The code does not show when that could happen, because dispatch
  checks for an open screen first. **Unverified**: whether a screen can open between dispatch and vanilla's
  `handleInputEvents` in the same tick. A diagnostic would settle it (see `05-design-decisions-roadmap.md`, item R12).
- `CHAT:` and `SLOT:` do not go through the dispatcher, so they are not dropped while a screen is open
  (`ThorHudClient.java:182-188`).

Forge:

```
dispatch: if screen open: close it for INVENTORY/E/ESCAPE/PAUSE/CLOSE, else drop
          else pressForOneTick(binding): KeyMapping.click(key); setDown(true); PENDING_RELEASE.add
tick:     release: setDown(false)
```

(`CommandDispatcherForge.java:97-113,181-185`.) `KeyMapping.click` is the public path that increments the press
count, the same counter Fabric's accessor reaches. Its behaviour is **unverified** here.

## 9. Chat relay

Receive (`ChatRelay.java:89-100,144-158`):

```
on CHAT event or on GAME event (skip if overlay):
  segments = flatten(message)           # visit with Style.EMPTY; merge same-colour neighbours
  if empty: return
  synchronized(HISTORY): addLast; drop oldest beyond 100
  server.broadcastChat(segments)
```

The event fires on the client thread. The backlog and the broadcast both happen there, so the only lock is for the
copy handed to a new client (`ChatRelay.java:103-107`).

Send (`ChatRelay.java:131-142`): normalise (`StringUtils.normalizeSpace`, `StringHelper.truncateChat`), then
`/` routes to `sendChatCommand` and other text to `sendChatMessage`. The send runs on the tick, from `CHAT:`.

## 10. Container relay

Capture (`ContainerRelay.java:118-147`):

- Read `player.currentScreenHandler`, with `slot.getStack()` and `slot.canInsert(cursor)` for each slot.
- Set the role indices: for `PlayerScreenHandler` the constants, otherwise `size - 36` and `size - 9` (`:142-145`).
- The handler type is read with `Registries.SCREEN_HANDLER.getId(handler.getType())`, guarded against the throw that
  happens for the player's own handler (`:155-165`).

Click (`ContainerRelay.java:179-222`):

1. Parse `SLOT:<syncId>,<slotId>,<button>,<ACTION>`. Refuse anything malformed.
2. Refuse if `syncId` differs from the open handler's `syncId` (`:203-208`).
3. Refuse if `slotId` is out of range, except `-999` (`:210-213`).
4. Call `interactionManager.clickSlot(...)`, then clear the cache (`:216-221`).

The refusal happens before the game changes anything. The class comment at `ContainerRelay.java:170-178` gives the
reason: vanilla's own check runs only after the client-side handler has been mutated.

## 11. Asset bundle and key-binding list

- Assets are read on demand from the resource manager, once per key per request. There is no cache, so each new connection
  and each `ASSETS` request re-reads about 43 PNGs (`HudAssetCatalog.java:131-150`).
- A missing or failed read sends an asset with no data (`HudAssetCatalog.java:131-150`; `ThorHudClient.java:325,359` pass
  `orElse(null)`). The app is expected to fall back to its own copy. That behaviour is **unverified** here.
- Bindings are read per request from `options.allKeys` (`KeyBindingCatalog.java:73-93`). Labels and key names are
  translated on every call. There is no cache.

## 12. GameHudVisibility

- `install()` replaces each vanilla element with a wrapper once, at client init (`GameHudVisibility.java:85-95`).
- Each frame, the wrapper checks `SHOWN_IN_GAME` and calls the original only if the element is in the set.
- `setShownInGame` parses the whole payload into a new set, and replaces the current set only if the two differ
  (`:108-131`). The comparison is by set equality, so a repeated message does nothing.
- An empty payload means nothing is shown, the default (`:77-78`).
