# Ocellus Portals (RuneLite plugin)

Phase 2 of Ocellus in The Fractured Archive. The plugin watches the orange wall portals, works out
where their two lanes cross, and paints that crossing on the floor. Once it has seen one wave it
recognises which of the three fixed sequences is running and also marks the tile for the following
wave, so you can move before the next portals even appear.

## What it draws

| Marker | Meaning |
| --- | --- |
| Filled orange block, labelled `SAFE` or e.g. `O3` | The tiles where the orange row lane and orange column lane cross right now. `O3` = orange sequence, wave 3. |
| Dashed white outline, `NEXT` / `GO` | The tile for the following wave. Shows `GO` while the portals are down between waves. |
| Thin numbered outlines (off by default) | Every remaining wave of the recognised sequence. |
| Green outline, `TP` | The orange floor portal that moves you to the next quadrant. |
| Purple tint | The purple pools that appear before the floor portal. |
| Top-left panel | Recognised sequence, current wave, and which colour comes next (orange → red → green → orange). |

Everything is configurable under **Ocellus Portals** in the plugin list (colours, opacity, what to show).

## How it identifies things

These are the game's own ids, read from the cache released with the raid (20 Oct 2026):

| Thing | Type | Id | Game name |
| --- | --- | --- | --- |
| Orange wall portal (does not fire) | object, 2 tiles wide | 62684 | `TFA_OCELLUS_QUADRANT_WALL_INACTIVE` (model 62758, orange) |
| Purple wall portal (fires) | object, 2 tiles wide | 62685 | `TFA_OCELLUS_QUADRANT_WALL_ACTIVE` (model 62757, purple) |
| Orange floor portal | spot animation | 4271 / 4272 / 4273 | `VFX_OCELLUS_P2_SAFE_PORTAL_SPAWN / IDLE / DESPAWN` |
| Purple pool | spot animation | 4270 | `VFX_OCELLUS_P2_AOE_DODGE` |
| Arena | region | 16472, plane 1 | |

The safe zone is the set of tiles that are in the column span of the orange portal on the horizontal
line *and* in the row span of the orange portal on the vertical line. Both portals are two tiles wide,
so the crossing is normally a 2x2 block. The sequence tables in `PortalSequences.java` are the same
data as the Ground Markers tile pack.

## Installing (Windows)

RuneLite only loads plugins that are not on the Plugin Hub when it runs in **developer mode**, and
developer mode is off when the client is started through the normal launcher. `run-runelite-dev.bat`
handles both halves:

1. Start RuneLite normally at least once (so `%USERPROFILE%\.runelite\repository2` exists).
2. Keep `run-runelite-dev.bat` and `ocellus-portals-1.0.0.jar` in the same folder and double-click the `.bat`.
   It copies the jar into `%USERPROFILE%\.runelite\sideloaded-plugins` and starts RuneLite in developer
   mode using the Java runtime that ships with RuneLite.
3. In the client, enable **Ocellus Portals** in the plugin list.

Jagex account: you do not go through the Jagex launcher. In developer mode the client has its own
**Jagex Accounts** button in the sidebar: click it, choose **Log in to a Jagex account**, sign in in
the window that opens, then click your character and log in as normal. The sign-in is kept in the
Windows credential store, so later runs only need the `.bat`. (The RuneLite wiki's older
`--insecure-write-credentials` method is dead code in client 1.13.4; the flag is accepted but unused.)

Keep launching RuneLite the normal way every so often so the client files in `repository2` stay
up to date; the `.bat` reuses whatever version is there.

## Building it yourself

Standard RuneLite external-plugin layout. Either open the folder in IntelliJ IDEA (import as a
Gradle project) and run `OcellusPortalsPluginTest` with VM options `-ea`, or from a terminal:

```
gradlew.bat build
```

The jar lands in `build/libs/`. The build pulls the RuneLite client from `repo.runelite.net`
(`latest.release`), so it always compiles against the current client.

## Known limits

- The plugin has been compiled against RuneLite 1.13.4 and checked against the cache data, but it has
  not yet been run inside a live raid. If a marker is in the wrong place, turn on **Debug** in the
  config: it draws every tracked orange portal with its id and `H`/`V` lane direction, which is enough
  to see what went wrong.
- The floor portal's position is not fixed, so only the live marker exists for it (no prediction).
- Sources disagree on whether phase 2 has three or four quadrants; the plugin does not care, it just
  keeps following the cycle.
