# 03 - Data flow

Mermaid diagrams for the paths data takes through the mod. Each diagram names the classes it refers to, so it can be
checked against the module map in [01-architecture-modules.md](01-architecture-modules.md). The Fabric path is the
primary one. Differences on Forge are noted under each diagram.

## 1. Outbound: game state to the app

```mermaid
flowchart LR
  subgraph Game["Minecraft client thread (tick and events)"]
    TICK["END_CLIENT_TICK<br/>ThorHudClient.onClientTick"]
    CHAT_EV["CHAT and GAME events<br/>ChatRelay"]
    CONT_R["open screen handler<br/>ContainerRelay.capture"]
    MAP_R["world columns<br/>MapRenderer.render"]
    ICON_R["ItemStack<br/>ItemIconRenderer"]
    ASSET_R["resource manager<br/>HudAssetCatalog"]
    BIND_R["options.allKeys<br/>KeyBindingCatalog"]
  end
  TICK -->|"HudState, if changed"| SRV["HudStateServer.broadcast"]
  CHAT_EV -->|"segments"| SRV
  CONT_R -->|"ScreenHandlerState, if changed"| SRV
  TICK -->|"every 10 ticks, if clients"| MAP_R -->|"Tile"| SRV
  TICK -->|"up to 2 per tick"| ICON_R -->|"base64 PNG, async"| SRV
  TICK -->|"on connect and ASSETS"| ASSET_R -->|"base64 PNG"| SRV
  TICK -->|"on connect and BINDINGS"| BIND_R -->|"Entry list"| SRV
  SRV -->|"one JSON line per message<br/>127.0.0.1:48291"| APP["companion app<br/>(not in this repository)"]
```

Forge differences: the icon path is `ItemIconResolverForge` (texture file only, no `ItemIconRenderer`), there is no
asset path, and `MapRendererForge` uses `ImageIO` and is otherwise the same. Container state comes from
`ContainerRelayForge.capture`, which adds widgets and the title.

## 2. Inbound: app to game

```mermaid
flowchart LR
  APP["companion app"] -->|"one line per command<br/>127.0.0.1:48291"| READER["thorhud-reader thread<br/>HudStateServer.readLoop"]
  READER -->|"trimmed line"| Q[("incomingCommands<br/>ConcurrentLinkedQueue")]
  Q -->|"drained on tick,<br/>all lines"| ROUTE{"ThorHudClient<br/>command router"}
  ROUTE -->|"ICON:"| IQ[("icon queue<br/>in-flight set")]
  ROUTE -->|"ASSETS"| ASSET_OUT["broadcastHudAssets"]
  ROUTE -->|"BINDINGS"| BIND_OUT["broadcastBindings"]
  ROUTE -->|"HUD:keys"| VIS["GameHudVisibility.setShownInGame"]
  ROUTE -->|"CHAT:text"| CHAT_OUT["ChatRelay.send"]
  ROUTE -->|"SLOT:..."| CLICK["ContainerRelay.click"]
  ROUTE -->|"digits, BIND:id, named codes"| DISP["CommandDispatcher.dispatch"]
  DISP -->|"screen open"| DROP["drop and log"]
  DISP -->|"no screen"| PRESS["KeyBinding: counter++, setPressed(true)"]
  PRESS -->|"next tick"| VANILLA["vanilla handleInputEvents"]
  PRESS -->|"next END hook"| REL["CommandDispatcher.tick: setPressed(false)"]
```

Forge differences: the router is `ThorHudForgeClient.onClientTick` and adds `WIDGET:`, `SCREEN_CLICK:`, `SORT`,
`CLOSE_CONTAINER` and `SUPPRESS_CONTAINER:`. `ASSETS` is accepted and does nothing. Presses go through
`CommandDispatcherForge`, which uses `KeyMapping.click` and `setDown`.

## 3. Bootstrap on connect (Fabric)

```mermaid
sequenceDiagram
  autonumber
  participant App as companion app
  participant Acc as thorhud-accept thread
  participant Tick as client thread (tick)
  participant Srv as HudStateServer
  App->>Acc: TCP connect 127.0.0.1:48291
  Acc->>Srv: hello, before registration
  Srv-->>App: {"type":"hello","mod":"aynthor_secondscreen","protocol":1}
  Acc->>Srv: clients.add(socket), newClients.add(socket)
  Acc->>Acc: start thorhud-reader for this socket
  Note over Tick: next END_CLIENT_TICK with a player
  Tick->>Srv: pollNewClient()
  loop each HudAssetCatalog key
    Tick->>Srv: sendAssetTo(socket, key, png or null)
    Srv-->>App: {"type":"asset","assetId":...}
  end
  Tick->>Srv: sendBindingsTo(socket, snapshot)
  Srv-->>App: {"type":"bindings","bindings":[...]}
  loop ChatRelay.history, oldest first
    Tick->>Srv: sendChatTo(socket, segments)
    Srv-->>App: {"type":"chat","segments":[...]}
  end
  opt a container has been sent before
    Tick->>Srv: sendContainerTo(socket, lastSent)
  end
  opt a HUD snapshot has been sent before
    Tick->>Srv: sendStateTo(socket, lastHudState)
    Srv-->>App: HUD snapshot line
  end
```

Notes on the diagram:

- A broadcast from the tick thread can reach the socket between steps 2 and 3, because the socket is registered before
  the bootstrap runs (`HudStateServer.java:92-97`). See [02-algorithms-timing.md](02-algorithms-timing.md) section 5.
- On Forge the order is bindings, chat, the last snapshot (sent with `broadcast`, so to every client), then the container.
  No assets are sent (`ThorHudForgeClient.java:215-230`).
- Nothing in this sequence happens before a player exists (`ThorHudClient.java:142-146`).

## 4. Key press, end to end

```mermaid
sequenceDiagram
  autonumber
  participant App as companion app
  participant Rdr as thorhud-reader
  participant Tick as client thread
  participant CD as CommandDispatcher
  participant KB as KeyBinding (vanilla)
  App->>Rdr: "BIND:key.inventory"
  Rdr->>Tick: queued
  Note over Tick: tick N, END hook
  Tick->>CD: tick() (releases tick N-1)
  Tick->>CD: dispatch("BIND:key.inventory")
  CD->>CD: currentScreen == null ?
  CD->>KB: timesPressed += 1 (KeyBindingAccessor)
  CD->>KB: setPressed(true)
  CD->>CD: PENDING_RELEASE.add
  Note over Tick: tick N+1
  Tick->>KB: handleInputEvents reads wasPressed() and isPressed()
  Note over Tick: tick N+1, END hook
  Tick->>CD: tick() releases
  CD->>KB: setPressed(false)
```

If a screen is open at dispatch time, the press is dropped with a log line and nothing is queued
(`CommandDispatcher.java:140-143`).

## 5. Icon request

```mermaid
sequenceDiagram
  autonumber
  participant App as companion app
  participant Tick as client thread
  participant IR as ItemIconRenderer
  participant GPU as GPU readback
  participant Srv as HudStateServer
  App->>Tick: "ICON:minecraft:diamond_sword"
  Tick->>Tick: queue, add to in-flight set
  Note over Tick: up to 2 per tick
  Tick->>Tick: resolveStack (inventory first, else default)
  Tick->>IR: renderBase64Png(stack, callback)
  alt render state empty
    IR-->>Tick: callback(null)
  else render submitted
    IR->>GPU: copyTextureToBuffer (fenced)
    GPU-->>IR: fence signals, render thread
    IR-->>Tick: callback(base64)
  end
  alt base64 present
    Tick->>Srv: broadcastIcon
  else fallback
    Tick->>Tick: ItemIconResolver.resolveBase64Png
    alt fallback present
      Tick->>Srv: broadcastIcon
    else nothing
      Tick->>Srv: broadcastIconFailure
    end
  end
  Srv-->>App: {"type":"icon","itemId":...,"data":...} or no data
```

The callback runs on the render thread after the GPU fence, not on the tick that started the render
(`ItemIconRenderer.java:278-289`). The id stays in the in-flight set until the callback removes it.

## 6. Leaving and re-entering a world

```mermaid
stateDiagram-v2
  [*] --> NoPlayer
  NoPlayer --> HasPlayer: tick with player
  HasPlayer --> HasPlayer: tick with player, HUD and container deltas sent
  HasPlayer --> NoPlayer: tick without player
  note right of NoPlayer
    noplayer sent once on the transition (ThorHudClient.java:273-288)
    chat history cleared, lastHudState and container cache reset
  end note
  note right of HasPlayer
    bootstrap sent to any new client on the first tick with a player
  end note
```

## 7. Bind and retry

```mermaid
stateDiagram-v2
  [*] --> Binding
  Binding --> Listening: ServerSocket ok, bindFailure = null
  Binding --> Waiting: IOException, bindFailure set, stderr, chat once
  Listening --> Listening: accept, hello, register
  Listening --> Waiting: IOException from accept
  Waiting --> Binding: after 5000 ms
```

The chat line is posted by the tick thread, not by the accept thread (`ThorHudClient.java:244-259`). The flag that stops
repeats is cleared when the bind succeeds.
