# 03 - Socket protocol reference

Everything here is taken from the code on `origin/master`. Where the Fabric and Forge builds differ, both are given.
Field names are the JSON names Gson emits from the record component names. The companion app's parser is not in this
tree, so how it reads these messages is **unverified**.

## 1. Transport and framing

- TCP, bound to loopback only: `127.0.0.1` port `48291` (Fabric `ThorHudClient.java:106`, bound at
  `HudStateServer.java:78` with `InetAddress.getLoopbackAddress()`; Forge `ThorHudForgeClient.java:35`, bound at
  `forge/.../HudStateServer.java:146` with `InetAddress.getByName("127.0.0.1")`).
- One JSON object per line, UTF-8, terminated by `\n`. No length prefix, no framing beyond the newline.
- Inbound lines are trimmed; empty lines are dropped (`HudStateServer.java:136-141`). There is no maximum line length.
- No authentication, no handshake beyond the hello line, no heartbeat, no compression.
- Server-to-client writes are flushed per line. On Fabric, writes to one socket are synchronised per socket
  (`HudStateServer.java:299-306`). On Forge the broadcast path has no such lock (`forge/.../HudStateServer.java:201-225`).

## 2. Connection lifecycle

1. The accept thread (`thorhud-accept` on Fabric, `ThorHud-Accept` on Forge) accepts a connection (`HudStateServer.java:84`).
2. It writes the hello line **before** registering the client, so the hello is the first line on the wire
   (`HudStateServer.java:90`; Forge `forge/.../HudStateServer.java:155`).
3. The client is added to the broadcast list and to a new-client queue, and a reader thread is started
   (`HudStateServer.java:92-101`).
4. On the next client tick that has a player, the tick thread drains the new-client queue and sends the bootstrap set
   (see section 6).

Two points of note for the app author:

- The bootstrap is sent on the tick thread and only after a player exists (`ThorHudClient.java:142-146,194`). A client
  connecting at the main menu gets the hello and nothing else until a world loads.
- A client that disconnects is removed from the broadcast list when a write fails or its reader sees EOF
  (`HudStateServer.java:146,307-313`; `forge/.../HudStateServer.java:196-198,209-210`). The mod never reconnects; the app does.

## 3. Outbound messages (mod to app)

### 3.1 `hello`

First line of every connection.

```json
{"type":"hello","mod":"aynthor_secondscreen","protocol":1}
```

Both builds use the same constants (`HudStateServer.java:37,41`; `forge/.../HudStateServer.java:28-29`). The `mod` value is
not the Fabric mod id (`aynthor_secondscreen_v1_21`). The protocol integer is bumped only for changes that break older
apps (`HudStateServer.java:39-40`).

### 3.2 HUD snapshot (no `type` field)

Fabric record `HudState` (`src/client/java/com/exojosh/client/HudState.java:24-63`):

| Field | Type | Meaning |
|---|---|---|
| `health`, `maxHealth` | float | Half-heart units (20.0 = 10 hearts) |
| `absorption` | float | Golden-heart health on top of `health` |
| `armor` | int | Armor points |
| `food` | int | Hunger points |
| `xpLevel`, `xpProgress` | int, float | Level, and 0 to 1 fraction toward the next |
| `selectedSlot` | int | Active hotbar slot, 0 to 8 |
| `air`, `maxAir` | int | Breath in ticks and the current maximum (raw, not reduced to bubbles) |
| `gameMode` | string or null | `SURVIVAL`, `CREATIVE`, `ADVENTURE` or `SPECTATOR`; absent when unknown |
| `heartType` | string | `NORMAL`, `POISONED`, `WITHERED` or `FROZEN` |
| `hardcore` | boolean | Hardcore world |
| `hotbar` | array of 9 slots | Slot 0 to 8 |
| `offhand` | slot | Always present; `minecraft:air` when empty |

Slot shape (`HudState.java:76-82`):

```json
{"itemId":"minecraft:dirt","count":1,"damage":0,"maxDamage":0,"hasGlint":false}
```

- `damage` and `maxDamage` are both 0 for non-damageable items. The app should treat that as "no durability bar", not "fully worn".
- An empty slot is `{"itemId":"minecraft:air","count":0,"damage":0,"maxDamage":0,"hasGlint":false}`.

Forge record `HudState` (`forge/.../HudState.java:17-41`) has every field above and also the following fields:

| Field | Type | Notes |
|---|---|---|
| `armorToughness` | float | Armor toughness attribute |
| `saturation`, `exhaustion` | float | `FoodData` levels |
| `armorSlots` | array of 4 | `FEET`, `LEGS`, `CHEST`, `HEAD`, each `{slot,itemId,count,damage,maxDamage}` (`ModHudCompat.java:77-83`) |
| `effects` | array | `{id,name,durationTicks,amplifier,beneficial}` per active effect (`ModHudCompat.java:61-67`) |
| `gun` | object | `{active,gunId,gunName,ammo,maxAmmo,fireMode,reserveAmmo}`, from TACZ reflection (`ModHudCompat.java:39-47`) |
| `survival` | object | Thirst, temperature and stamina from LSO and ParCool reflection (`ModHudCompat.java:49-59`) |
| `target` | object | `{type,name,mod,health,maxHealth}` for the block or entity under the crosshair (`ModHudCompat.java:69-75`) |

The HUD snapshot is sent only when it differs from the last one sent (structural equality on the record). Fabric:
`ThorHudClient.java:224-228`. Forge: `ThorHudForgeClient.java:161-165`. The last snapshot is replayed to each new
connection.

### 3.3 `noplayer`

```json
{"type":"noplayer"}
```

Sent once on the transition from "has a player" to "no player" (`ThorHudClient.java:273-288`;
`ThorHudForgeClient.java:182-193`). The app should keep "no player" until a snapshot arrives.

### 3.4 `icon`

```json
{"type":"icon","itemId":"minecraft:diamond_sword","data":"<base64 PNG>"}
```

- A failed request sends the same shape with no `data` key. Fabric builds it with `data` set to null
  (`HudStateServer.java:189-191`); Gson omits null fields by default, and the code uses `new Gson()` with no options
  (`HudStateServer.java:43`). That the key is omitted on the wire is therefore **per Gson defaults, unverified against the bundled Gson version**.
  Forge uses a record without a `data` component (`forge/.../HudStateServer.java:78-81`), so the key is absent by construction.
- The PNG is 64 by 64 on Fabric (`ItemIconRenderer.java:91`). Forge serves the raw texture file, whose size depends on the resource pack.
- `broadcastIcon` sends to **all** connected clients, not only the requester (`HudStateServer.java:179-181,289-294`).

### 3.5 `asset`

```json
{"type":"asset","assetId":"heart_full","data":"<base64 PNG>"}
```

Fabric only. Sent for each key in `HudAssetCatalog` (43 keys; `HudAssetCatalog.java:40-116`). `data` is absent when the
resource stack has no such file (`HudAssetCatalog.java:131-150`). The full list of keys and vanilla paths is in
`HudAssetCatalog.java:44-115`. Keys cover hearts (including poisoned, withered, frozen and hardcore variants), armor,
food, air, XP bar, hotbar and selection sprites, the off-hand box, the enchantment glint, widget buttons, the container
slot, the bitmap font sheet, the dirt background and the map paper.

Sent in response to `ASSETS` to all clients (`ThorHudClient.java:173-176,357-361`), and to each new client on connect
(`ThorHudClient.java:324-326`).

### 3.6 `bindings`

```json
{"type":"bindings","bindings":[{"id":"key.inventory","label":"Inventory","category":"Inventory","boundKey":"E","unbound":false}]}
```

Fabric record `KeyBindingCatalog.Entry` (`KeyBindingCatalog.java:63-70`). Entries are sorted by category then label
(`KeyBindingCatalog.java:91`). The `id` is the translation key and the value the app sends back in `BIND:<id>`. The
display strings are translated on the mod side. Forge builds the same shape from `keyMappings` (`KeyBindingCatalogForge.java:18-24`).

Sent in response to `BINDINGS`, and to each new client on connect (`ThorHudClient.java:177-178,327`).

### 3.7 `chat`

```json
{"type":"chat","segments":[{"text":"<Steve> hi","color":16777215},{"text":" (dm)"}]}
```

- Segment `color` is an RGB integer, or absent when the colour is the default (`ChatRelay.java:50-51`; Gson omits null).
- Only colour survives; bold, italic and obfuscated are dropped (`ChatRelay.java:40-50`).
- The backlog of up to 100 messages is replayed to each new client in order, oldest first, after the asset and
  binding messages (`ThorHudClient.java:329-335`).

### 3.8 `container`

Fabric record `ContainerRelay.ScreenHandlerState` (`ContainerRelay.java:71-90`). Note that it carries its own `type`
field and is not wrapped:

```json
{"type":"container","syncId":3,"handlerType":"minecraft:generic_9x3","cursor":{...},"slots":[{"stack":{...},"mayPlace":true}],"playerStart":27,"hotbarStart":54,"armorStart":-1,"offhandIndex":-1}
```

| Field | Notes |
|---|---|
| `syncId` | The handler's sync id; required in every `SLOT:` click |
| `handlerType` | Registry id of the screen handler type; null for the player's own inventory |
| `cursor` | Slot shape; stack currently held on the mouse |
| `slots` | One entry per handler slot, each `{stack, mayPlace}`; `mayPlace` is `slot.canInsert(cursor)` |
| `playerStart`, `hotbarStart` | Index of the player's inventory and hotbar within `slots` |
| `armorStart`, `offhandIndex` | -1 unless the handler is the player's own inventory |

For the player's own inventory (`PlayerScreenHandler`) the indices come from its constants. Those values are
**unverified** in this spec. For any other handler, the mod assumes the player's 36 slots are the last 36, and the hotbar
the last 9 (`ContainerRelay.java:142-143`).

Forge record (`forge/.../ContainerRelayForge.java:32-64`) adds these fields:

- `title`: the open screen's title string, or null.
- `isScreenOpen`: whether the open screen is a container screen.
- `widgets`: a list of visible widgets `{id,label,className,x,y,width,height,active}`, with `id` being the index among visible widgets.
- Each slot carries `slotIndex`, `x` and `y` in addition to `stack` and `mayPlace`.
- Player-inventory indices are the constants `9`, `36`, `5` and `45` (`ContainerRelayForge.java:152-155`).

Sent on change, and replayed to each new client when a container is open (`ThorHudClient.java:339-343`;
`ThorHudForgeClient.java:225-228`). The mod re-sends after each accepted click by clearing its cache (`ContainerRelay.java:221`).

### 3.9 `map`

```json
{"type":"map","data":"<base64 PNG>","originX":-64,"originZ":120,"playerX":10.5,"playerZ":130.25,"yaw":180.0,"size":128}
```

Fabric record `MapResponse` (`HudStateServer.java:359-368`). `originX` and `originZ` are the world block coordinates of the
tile's top-left pixel. `yaw` follows vanilla (0 is south, clockwise). The tile is 128 by 128 (`MapRenderer.java:64`).

Sent every 10 ticks while a client is connected (`ThorHudClient.java:81,297-309`). Forge does the same
(`ThorHudForgeClient.java:149-159`).

## 4. Inbound commands (app to mod)

Each inbound line is one command. Commands are handled in arrival order within a tick. All queued commands are drained
in that tick, with no cap (`ThorHudClient.java:170-192`). The icon queue alone is capped at 2 renders per tick.

### 4.1 Fabric

| Line | Effect | Source |
|---|---|---|
| `1`..`9` | Press the matching `options.hotbarKeys` binding for one tick | `CommandDispatcher.java:145-149` |
| `BIND:<id>` | Press `KeyBinding.byId(<id>)` for one tick; unknown ids are logged and ignored | `CommandDispatcher.java:151-165` |
| `ICON:<itemId>` | Queue an icon render. Empty or `minecraft:air` is ignored and gets **no reply** | `ThorHudClient.java:171-172,363-368` |
| `ASSETS` | Re-send the whole asset bundle to all clients | `ThorHudClient.java:173-176` |
| `BINDINGS` | Re-send the binding list to all clients | `ThorHudClient.java:177-178` |
| `HUD:<keys>` | Set the full set of elements the game should draw (comma-separated, may be empty) | `ThorHudClient.java:179-181`, `GameHudVisibility.java:108-131` |
| `CHAT:<text>` | Say `<text>` as the player, or run it if it starts with `/` | `ThorHudClient.java:182-183`, `ChatRelay.java:131-142` |
| `SLOT:<syncId>,<slotId>,<button>,<ACTION>` | Perform a slot click if `syncId` matches the open handler. `slotId` may be `-999` | `ThorHudClient.java:184-188`, `ContainerRelay.java:179-222` |
| `INVENTORY`, `DROP`, `SWAP`, `USE`, `ATTACK`, `JUMP`, `SNEAK` | Press the named binding (named codes) | `CommandDispatcher.java:93-99` |
| `E`, `R`, `G`, `H`, `K`, `F` | Legacy aliases. `E` is inventory; `R` and `F` are swap hands; `G` is drop; `H` is use; `K` is attack | `CommandDispatcher.java:106-111` |
| anything else | Logged as an unknown command and ignored | `CommandDispatcher.java:167-171` |

`ACTION` in `SLOT:` must be the name of a `SlotActionType` constant. `tools/capture_protocol.py` sends `PICKUP`
(`tools/capture_protocol.py:193-195`). Any other name makes the mod log "Unparseable slot click" and drop the line
(`ContainerRelay.java:197-201`).

While a screen is open, all presses in 4.1 (digits and named codes) are dropped and logged
(`CommandDispatcher.java:140-143`). `CHAT:`, `SLOT:`, `ICON:`, `HUD:`, `ASSETS` and `BINDINGS` are not dropped, because
they are handled before the dispatcher is reached.

### 4.2 Forge additions

The Forge build handles the same prefixes (`forge/.../ThorHudForgeClient.java:92-137`), and also these:

| Line | Effect | Source |
|---|---|---|
| `ASSETS` | Accepted and ignored. No asset bundle is sent | `ThorHudForgeClient.java:96-97` |
| `WIDGET:<int>` | Press the visible widget at that index on the open screen (Button `onPress`, or `onClick` at its centre) | `ThorHudForgeClient.java:107-111`, `ContainerRelayForge.java:210-231` |
| `SCREEN_CLICK:<x>,<y>` | Call `mouseClicked(x, y, 0)` on the open screen | `ThorHudForgeClient.java:114-122`, `ContainerRelayForge.java:233-238` |
| `SORT` | Press the first widget whose class or label contains `sort` or `miniinventorybutton` | `ThorHudForgeClient.java:112-113`, `ContainerRelayForge.java:240-259` |
| `CLOSE_CONTAINER` | Close the container and clear the screen | `ThorHudForgeClient.java:123-131` |
| `SUPPRESS_CONTAINER:<true|false>` | Set whether container screens are hidden on the top display. The default is true | `ThorHudForgeClient.java:132-134`, `ContainerRelayForge.java:29,76-81` |

Forge's `CommandDispatcherForge.dispatch` also accepts these named codes that the Fabric side does not have:
`CHAT`/`T` (chat key), `USE`/`H`, `ATTACK`/`K`, `TAB`/`PLAYER_LIST`, `SPRINT`, `MAP`/`M` (resolved by name, see
`CommandDispatcherForge.java:65-81`), `ESCAPE`/`PAUSE` (pause), `F3`/`DEBUG` (debug overlay toggle) and `F5`/`PERSPECTIVE`
(camera cycle). It also maps `Q` to drop and `F` and `R` to swap hands (`CommandDispatcherForge.java:27-45`). The Forge
`Q` binding is not on the Fabric side.

Forge runs `ESCAPE`, `INVENTORY`, `E`, `CLOSE`, and `PAUSE` on an open screen to close it, and drops every other command
(`CommandDispatcherForge.java:101-113`).

## 5. Outbound messages that are not replies

Some messages are pushed without a request:

- HUD snapshot, when it changes (section 3.2).
- `noplayer`, on the transition (section 3.3).
- `map`, every 10 ticks while connected (section 3.9).
- `container`, when the open handler's contents change (section 3.8).
- `chat`, for every new message from the game (section 3.7).

Icons, assets and bindings are sent only in response to a request or on connect.

## 6. Bootstrap order on a new connection (Fabric)

`ThorHudClient.java:321-355`, after the hello line:

1. Every asset, in `HudAssetCatalog` key order (`asset` messages).
2. The binding list (`bindings`).
3. The chat backlog, oldest first (`chat` messages).
4. The current container state, if a container state has been sent before (`container`).
5. The last HUD snapshot, if one has been sent before (HUD state line).

Forge sends bindings, the chat backlog, the last snapshot (to every client, not just the newcomer, see
`ThorHudForgeClient.java:221-224`), and the container state. It does not send assets. The order is different
(`ThorHudForgeClient.java:215-229`).

## 7. Known protocol gaps

These are observed in the code and not yet handled.

- An `ICON:` request for `minecraft:air` or an empty id is dropped without a reply (`ThorHudClient.java:364`). The app waits
  for an answer that never comes. `CLAUDE.md` ("Icon request reliability") says every request gets an answer, so the code
  and that note disagree here.
- Forge's `pushBundleToNewClients` sends the last snapshot to all clients, not only the newcomer. Harmless, but it
  differs from Fabric (`ThorHudForgeClient.java:221-224`).
- The Forge `HudState` and container wire shapes differ from Fabric's. An app written against one build will see extra or missing keys on the other.
- No `hello` version negotiation beyond the single integer.
