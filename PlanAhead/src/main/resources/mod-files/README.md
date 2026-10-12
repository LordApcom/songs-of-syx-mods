# Plan Ahead

A Song of Syx mod for players who like to design their settlement layout long before
they have the matching technology to actually run every building.

## What it does

Vanilla Song of Syx hides (or disables) buildings whose tech is not yet researched.
This means you can't even *plan* where they will eventually go: you have to leave
empty space and hope you guessed the footprint right.

**Plan Ahead** removes that restriction:

- Every building is always selectable in the build menu, regardless of tech.
- You can drag out the footprint exactly like a normal placement.
- If the building's tech is **already researched**, it is constructed normally.
- If the tech is **still locked**, the construction is automatically created in
  the game's existing Planning Mode (dormant): it reserves the space, costs no
  workers and no resources, and waits.
- A tech-locked construction stays dormant. The game will refuse to activate it
  for as long as the tech remains locked. The moment you finish the research,
  you can activate it like any other plan.

## Why it exists

So you can lay out your full city blueprint up-front. Place the temple, the
academy, the late-game industries, the high-tier housing, all in their final
spots, and watch the city slowly come alive as the corresponding research lines
finish. No more "I'll figure out where this goes when I get there", no more
relocating half a quarter because the late-game footprint didn't fit.

## How it works (technical)

- The build menu's `BRoom` buttons are reflectively patched to remain
  clickable when tech-locked, and their click handler is wrapped to perform
  placement directly.
- While the player is dragging out a tech-locked building's footprint, the
  global `SETT.JOBS().planMode` flag is forced ON, so the resulting
  `ConstructionInstance` is created with `active = false` (dormant).
- A per-tick watchdog scans every active construction, and if its blueprint
  has unmet tech requirements (`reqs.passes(player) == false`) it forces the
  construction back into the dormant state, blocking manual activation until
  the tech is unlocked.

## Compatibility

Built against game version `0.71.31` (V71), Java 21.
Uses only reflection on stable internals; should be safe to remove from a
save (the game's `ScriptEngine` skips unknown script data on load).

### Better Blueprints (by Nerond)

Plan Ahead is designed to coexist with Better Blueprints. The two mods touch
disjoint subsystems: Better Blueprints overhauls the saved-prints / blueprint
book (`view/sett/ui/room/prints/`, `view/sett/ui/room/copy/`,
`settlement/room/main/copy/`), while Plan Ahead patches the bottom build
menu and the construction state.

Plan Ahead also preserves the vanilla `Ctrl+click` shortcut that opens the
saved-prints picker for the clicked building, even when the building is
tech-locked. That means you can drop a saved blueprint of a tech-locked
building (e.g. a temple layout) onto the map: every individual tech-locked
construction it spawns is caught by Plan Ahead's per-tick watchdog and
forced into the dormant Planning state, exactly as if you had placed it
manually.
