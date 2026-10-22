# AYN Thor Second Screen Design Spec

How each part of the mod works: module structure on both loaders, the tick loop and its timing, the data flow, the
interfaces between parts, error handling, device constraints and the reasons behind the main decisions.
Companion to the [application spec](../application-spec/README.md), which says what the mod does and what it sends.
Facts are from reading the code at `origin/master` (commit `83cd276`) on 2026-10-22. Path references are repo-relative
with `:line` anchors. Anything not traced in the code, or not measured, is marked **unverified**.

| # | File | Topic |
|---|---|---|
| 01 | [01-architecture-modules.md](01-architecture-modules.md) | Modules on the Fabric and Forge sides, threads, and what each class owns |
| 02 | [02-algorithms-timing.md](02-algorithms-timing.md) | Tick loop, change detection, bind retry, icon queue, map cadence, input press lifecycle, chat backlog |
| 03 | [03-data-flow.md](03-data-flow.md) | Mermaid diagrams for the data path, bootstrap, input press, and icon request |
| 04 | [04-interfaces-error-handling.md](04-interfaces-error-handling.md) | Internal interfaces and wire contracts, error handling, device and OS constraints |
| 05 | [05-design-decisions-roadmap.md](05-design-decisions-roadmap.md) | Decisions the code records, rejected alternatives, and open items |

**Plain summary.** One process holds everything: the game, the mod and a socket server. The mod hooks the client tick
(Fabric `END_CLIENT_TICK`, Forge `TickEvent.ClientTickEvent` END), builds small immutable snapshots, writes changes as
newline-delimited JSON to a loopback socket, and reads single-line commands back. Key presses are not applied directly:
they are set on the key binding for one tick, so the game's own input handling consumes them as if a key had been
pressed. Chat, slot clicks and icon requests are performed on the tick they are read.
