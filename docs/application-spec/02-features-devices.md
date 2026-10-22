# 02 - Features, devices and loader targets

This file lists what each half of the mod provides, as the code implements it. Features the companion app draws or
handles are described only as the README states them, and are marked **unverified** because the app is not in this tree.

## 1. HUD elements that can move to the second screen

The app switches these elements between screens. The wire key is what the app sends in `HUD:<keys>`.

| Key | Vanilla element (Fabric) | Vanilla overlay (Forge) | Fabric source | Forge source |
|---|---|---|---|---|
| `armor` | `VanillaHudElements.ARMOR_BAR` | `ARMOR_LEVEL` | `GameHudVisibility.java:52` | `GameHudVisibilityForge.java:20` |
| `bubbles` | `VanillaHudElements.AIR_BAR` | `AIR_LEVEL` | `GameHudVisibility.java:53` | `GameHudVisibilityForge.java:21` |
| `hearts` | `VanillaHudElements.HEALTH_BAR` | `PLAYER_HEALTH` | `GameHudVisibility.java:54` | `GameHudVisibilityForge.java:22` |
| `hunger` | `VanillaHudElements.FOOD_BAR` | `FOOD_LEVEL` | `GameHudVisibility.java:55` | `GameHudVisibilityForge.java:23` |
| `xp_bar` | `EXPERIENCE_LEVEL` and `INFO_BAR` | `EXPERIENCE_BAR` | `GameHudVisibility.java:58` | `GameHudVisibilityForge.java:24` |
| `hotbar` | `VanillaHudElements.HOTBAR` | `HOTBAR` | `GameHudVisibility.java:59` | `GameHudVisibilityForge.java:25` |

- The Fabric constants come from `net.fabricmc.fabric.api.client.rendering.v1.hud` (`GameHudVisibility.java:3-4`).
  Their names and existence against Fabric API 0.141.5 are **unverified** (`CLAUDE.md`, "Known unverified/fragile areas").
- Fabric wraps each element with `HudElementRegistry.replaceElement` and draws the original only when the key is in the
  shown-in-game set (`GameHudVisibility.java:85-95`). Forge cancels the matching `RenderGuiOverlayEvent.Pre` when the
  element is not in the shown-in-game set (`GameHudVisibilityForge.java:45-60`).
- The off-hand box has no key. The app sends it as an `offhand` element; unknown keys are ignored on Fabric with a log
  line, and ignored silently on Forge (`GameHudVisibility.java:116-119`, `GameHudVisibilityForge.java:70-72`).
- The app's full element list, including `offhand` and any settings it stores, lives in the app repository and is not in this tree.

## 2. Snapshot content (HUD state)

Sent on change, no `type` field (see [03-protocol-reference.md](03-protocol-reference.md)).

| Field | Fabric (`HudState.java:24-63`) | Forge (`forge/.../HudState.java:17-41`) |
|---|---|---|
| health, maxHealth, absorption | yes | yes |
| armor | armor points | armor points |
| armorToughness, saturation, exhaustion | no | yes |
| food, xpLevel, xpProgress | yes | yes |
| selectedSlot | yes | yes |
| air, maxAir | raw ticks | raw ticks |
| gameMode | `interactionManager.getCurrentGameMode().name()` or null | `gameMode.getPlayerMode().name()` or null |
| heartType | `POISONED`, `WITHERED`, `FROZEN` or `NORMAL` in that precedence (`HudState.java:89-94`) | same precedence (`HudState.java:50-55`) |
| hardcore | yes | yes |
| hotbar (9 slots) | yes | yes |
| offhand | yes (always present, `minecraft:air` when empty) | yes |
| armorSlots (FEET, LEGS, CHEST, HEAD) | no | yes (`ModHudCompat.java:363-381`) |
| effects | no | yes (`ModHudCompat.java:344-358`) |
| gun, survival, target | no | yes, see section 6 |

Each hotbar slot carries `itemId`, `count`, `damage`, `maxDamage` and `hasGlint`. Damage and max are 0 for items that
are not damageable (`HudState.java:65-75`). Forge derives these from `isDamageableItem()`, `getDamageValue()`,
`getMaxDamage()` and `hasFoil()` (`forge/.../HudState.java:78-87`). The Yarn/Mojang method names are **unverified**
against a compiled build.

## 3. Map

- A 128 by 128 tile centred on the player, one block per pixel (`MapRenderer.java:64,69`; `MapRendererForge.java:28,30`).
- Colour and brightness follow the vanilla map algorithm, using the same thresholds (`MapRenderer.java:220-246`). The
  file header says this is taken from decompiled 1.21.11 source; that claim is **unverified** here.
- Under a ceiling dimension (Nether-like), the scan starts just above the player's head rather than at the heightmap
  (`MapRenderer.java:110-113,154-156`). Documented as a deliberate deviation.
- The tile is rendered at most once per 10 ticks and only when a socket client is connected
  (`ThorHudClient.java:81,297-309`; `ThorHudForgeClient.java:149-159`).
- Forge encodes the tile with `javax.imageio.ImageIO` (`MapRendererForge.java:139-142`). Fabric encodes it with the
  NativeImage write path through `NativeImageInvoker` (`MapRenderer.java:256-264`).

## 4. Item icons

- **Fabric primary path.** `ItemIconRenderer` renders the item through the game's GUI model pipeline into a 64 by 64
  offscreen texture, reads it back and encodes PNG (`ItemIconRenderer.java:84-151`). Icons are isometric and tinted as
  the model dictates. The enchantment glint is deliberately not rendered (`ItemIconRenderer.java:244-260`); the app
  draws it.
- **Fabric fallback.** If the render returns nothing, `ItemIconResolver` reads the raw `textures/item/` or
  `textures/block/` PNG (`ItemIconResolver.java:43-50`). If that fails, the mod sends an explicit failure
  (`ThorHudClient.java:386-409`).
- **Forge.** Only the raw-texture resolver exists (`ItemIconResolverForge.java:16-23`). There is no offscreen render, so Forge icons are
  flat textures. A player's actual stack is not preferred on Forge; the mod looks up the item id directly.
- Icons are requested by the app with `ICON:<itemId>`. Up to 2 renders run per tick on Fabric
  (`ThorHudClient.java:74`); Forge drains up to 2 per tick with a synchronous texture read (`ThorHudForgeClient.java:200-213`).

## 5. Chat

- **Receive.** Fabric hooks `ClientReceiveMessageEvents.CHAT` and `.GAME` and skips the action bar (`ChatRelay.java:89-100`).
  Forge hooks `ClientChatReceivedEvent` and skips the system overlay case (`ChatRelayForge.java:36-42`). Forge does not
  distinguish CHAT from GAME events.
- **Styling.** Only colour is kept; bold, italic and obfuscated are dropped (`ChatRelay.java:40-50`). Adjacent runs of the same
  colour are merged (`ChatRelay.java:182-188`).
- **Backlog.** The last 100 messages are kept (`ChatRelay.java:72`; `ChatRelayForge.java:24`) and replayed to each new
  connection. The backlog is cleared when the player leaves a world.
- **Send.** Fabric normalises and truncates text, then routes `/`-prefixed text to `sendChatCommand` and other text to
  `sendChatMessage` (`ChatRelay.java:131-142`). Forge does the same with `connection.sendCommand` and `connection.sendChat`
  (`ChatRelayForge.java:56-68`).
- Chat sending is not blocked by an open screen on either side (`ThorHudClient.java:182-183`; `ThorHudForgeClient.java:103-104`).

## 6. Container (open screen) relay

- **Fabric** streams `player.currentScreenHandler`, not the inventory (`ContainerRelay.java:19-26`). Each slot carries
  its stack and `mayPlace` (`ContainerRelay.java:68,131`). The message gives the cursor stack, the handler type from the
  registry (null for the player's own inventory), and the role indices `playerStart`, `hotbarStart`, `armorStart` and
  `offhandIndex` (`ContainerRelay.java:118-147`). For the player's own inventory the indices come from
  `PlayerScreenHandler` constants (**values unverified**). Other handlers assume the player's 36 slots are last.
- **Forge** streams `player.containerMenu` with extra fields: `title`, `isScreenOpen`, each slot's `slotIndex`, `x` and `y`,
  and a list of visible `widgets` (`ContainerRelayForge.java:32-64,102-157`). Player-inventory indices are hard-coded to
  9, 36, 5 and 45 (`ContainerRelayForge.java:152-155`).
- **Click.** Fabric accepts `SLOT:<syncId>,<slotId>,<button>,<ACTION>` only when `syncId` matches, and performs it with
  `interactionManager.clickSlot` (`ContainerRelay.java:179-222`). Forge does the same with `gameMode.handleInventoryMouseClick`
  (`ContainerRelayForge.java:169-208`). `-999` (outside the window) is passed through on both sides.
- **Forge-only controls.** `WIDGET:<id>` presses a visible widget by index (`ThorHudForgeClient.java:107-111`,
  `ContainerRelayForge.java:210-231`). `SCREEN_CLICK:<x>,<y>` sends a mouse click to the screen
  (`ContainerRelayForge.java:233-238`). `SORT` presses a widget whose class or label contains "sort" or "miniinventorybutton"
  (`ContainerRelayForge.java:240-259`). `CLOSE_CONTAINER` closes the container (`ThorHudForgeClient.java:123-131`).
  `SUPPRESS_CONTAINER:<bool>` controls whether the top screen draws container screens (`ContainerRelayForge.java:29,76-81`).
  The suppression default is **true**, so the Forge build hides container screens on the top display until the app
  turns it off. This is a behaviour the code shows; whether the app ever sends `false` is **unverified** here.
- Fabric has no equivalents for these Forge-only commands.

## 7. Input

- **Keybinding list.** Fabric enumerates `options.allKeys`, including bindings added by other mods (`KeyBindingCatalog.java:73-93`).
  Each entry has `id`, `label`, `category`, `boundKey` and `unbound`. The list is sent on connect, on request with `BINDINGS`,
  and is a snapshot (`KeyBindingCatalog.java:43-45`). Forge enumerates `options.keyMappings` (`KeyBindingCatalogForge.java:26-44`).
- **Press.** `BIND:<id>` finds the binding and presses it for one tick (`CommandDispatcher.java:151-165`). Fabric looks the id up
  with `KeyBinding.byId`. Forge scans `keyMappings` for the name (`CommandDispatcherForge.java:163-172`).
- **Hotbar.** A digit `1`-`9` presses the matching `hotbarKeys` (Fabric) or `keyHotbarSlots` (Forge) binding.
- **Named codes.** Both sides keep a table of action names (`INVENTORY`, `DROP`, `SWAP`, `USE`, `ATTACK`, `JUMP`, `SNEAK`) and
  several older single-letter aliases (`E`, `R`, `G`, `H`, `K`, `F`) (`CommandDispatcher.java:93-111`;
  `CommandDispatcherForge.java:27-45`). The letter aliases are marked for removal in the code comments.
- **Forge-only codes.** `ESCAPE`/`PAUSE` pause the game, `F3`/`DEBUG` toggle the debug screen, `F5`/`PERSPECTIVE` cycle the camera,
  `MAP`/`M` press a map binding chosen by name (`CommandDispatcherForge.java:97-161`, `findMapKey` at `:65-81`).
- **Screen-open rule.** On Fabric, all binding presses are dropped while a screen is open (`CommandDispatcher.java:134-143`).
  On Forge, `INVENTORY`/`E`/`ESCAPE`/`PAUSE`/`CLOSE` close an open screen and every other command is dropped
  (`CommandDispatcherForge.java:101-113`).

## 8. Device and runtime targets

| | Fabric mod (`src/`) | Forge port (`forge/`) |
|---|---|---|
| Minecraft | 1.21.11 (`gradle.properties:10`; `fabric.mod.json:36` `~1.21.11`) | 1.20.1 (`forge/gradle.properties:4`; `mods.toml:23` `[1.20.1,1.21)`) |
| Loader | Fabric Loader >= 0.19.3 (`fabric.mod.json:35`) | Forge >= 47 (`mods.toml:16`); build uses Forge 47.4.0 (`forge/gradle.properties:5`) |
| Required API | Fabric API, `*` (`fabric.mod.json:38`) | none beyond Forge (`forge/build.gradle:43-45`) |
| Java | 21 (`build.gradle:69,81-82`; `fabric.mod.json:37`) | 17 (`forge/build.gradle:19`) |
| Side | client-only HUD logic; `"environment": "*"` in the mod JSON (`fabric.mod.json:17`) | `DistExecutor` runs client wiring only on `Dist.CLIENT` (`AynThorSecondScreenForge.java:17-23`) |
| Launcher named in README | Zalith Launcher 2 (`README.md:46-47`) | not named in the Forge sources |
| Target device | AYN Thor (dual screen), per README | intended for the DeceasedCraft 1.20.1 modpack, per `docs/forge-1.20-port-plan.md`. Not verified in this repository |

- **Hardware.** The app runs on a second Android presentation display (`README.md:44-45`). The mod has no device-specific code.
  Everything listens on `127.0.0.1` so nothing leaves the device.
- **Forge optional mods, by reflection.** `ModHudCompat` reads TACZ (guns), Legendary Survival Overhaul (thirst and
  temperature) and ParCool (stamina) by name, and does nothing when a class is missing (`ModHudCompat.java:25-35,171-339`).
  The forge build declares none of these as dependencies (`forge/build.gradle:43-45`), so the reflection runs only when
  they happen to be installed. The Fabric build has no equivalent.

## 9. Forge target parity

| Area | Fabric | Forge |
|---|---|---|
| HUD element hide and show | yes | yes |
| HUD snapshot | yes | yes, with extra fields |
| Texture bundle (`ASSETS`, asset messages) | yes (`ThorHudClient.java:357-361`) | no. `ASSETS` is accepted and ignored (`ThorHudForgeClient.java:96-97`) |
| Key-binding list | yes | yes |
| Chat both ways | yes | yes (no CHAT/GAME split) |
| Container relay | yes | yes, plus widgets, title and suppression |
| Item icon render (isometric) | yes | no, texture only |
| Map tile | yes | yes |
| Mod-specific extras (TACZ, LSO, ParCool, target block or entity) | no | yes |
| Protocol version | 1 | 1 |
| Forge-only commands (`WIDGET`, `SCREEN_CLICK`, `SORT`, `CLOSE_CONTAINER`, `SUPPRESS_CONTAINER`, `ESCAPE`, `F3`, `F5`, `MAP`, `TAB`, `SPRINT`) | no | yes |

The protocol version is the same constant, 1, on both sides (`HudStateServer.java:41`; `forge/.../HudStateServer.java:29`).
Both sides use the same identity string `aynthor_secondscreen` in the hello line (`HudStateServer.java:37`;
`forge/.../HudStateServer.java:28`). The Forge `HudState` has more fields, so an app built for Fabric will see extra keys
when pointed at Forge. Whether the app tolerates that is **unverified** here.
