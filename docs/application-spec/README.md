# AYN Thor Second Screen - Application Spec

What this repository is, what it does for a player, what it takes in and sends out, and the rules the code must follow.
Written from a read of the source at `origin/master` (commit `83cd276`) on 2026-10-22. Path references are repo-relative,
with `:line` anchors against that commit. Anything that could not be checked against the source, a build, or a device is
marked **unverified**. The companion [design spec](../design-spec/README.md) explains how each part works.

Scope: the Fabric 1.21.11 mod under `src/` and the Forge 1.20.1 port under `forge/`. The Android companion app is in a
separate repository and is not in this tree, so anything said about how the app draws or behaves is **unverified** here.

| # | File | Topic |
|---|---|---|
| 01 | [01-purpose-scope.md](01-purpose-scope.md) | Purpose, scope, users and day-to-day workflows |
| 02 | [02-features-devices.md](02-features-devices.md) | Features per HUD element, container, chat, input, and device and loader targets |
| 03 | [03-protocol-reference.md](03-protocol-reference.md) | Loopback socket: framing, every outbound message, every inbound command |
| 04 | [04-configuration-requirements.md](04-configuration-requirements.md) | Build configuration, versions, runtime requirements, CI |
| 05 | [05-testing-verification.md](05-testing-verification.md) | What is tested, what is only built, and the manual checks available |
| 06 | [06-limitations-glossary.md](06-limitations-glossary.md) | Known limitations, documents that have drifted from the code, unverified items, glossary |

**Plain summary.** The mod hides Minecraft's own HUD on the top screen and streams the same information over
`127.0.0.1:48291` as newline-delimited JSON. A companion app on the bottom screen draws it, and sends taps and key
presses back over the same socket. The mod does not lay out the second screen; it supplies data, and it also renders
item icons and map tiles as PNG images for the app. The socket has no authentication beyond being bound to loopback.
