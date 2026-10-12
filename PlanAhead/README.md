# Plan Ahead

Code mod for Songs of Syx V71 (0.71.44). Requires Polyphon.

## What it does
- Every building is clickable in the build menu, even when its technology is still locked.
- You drag out the area as usual. If the technology is already researched, it builds as always.
- If it's still locked, the construction is created in the game's Planning Mode: it reserves the space, with no workers or resources.
- The game refuses to activate the plan (Activate tool) while the technology is locked. After the research, activate it like any other plan.
- Also works for buildings from data mods: any room with a technology requirement is handled the same way.
- Paste/copy (Copy room, bulk copy) and saved blueprints (Ctrl+click / blueprint picker) also accept locked rooms; they become dormant plans.
- Decorations that cost resources (statues, etc.) are regular rooms and follow the same flow.

## How to use
Open the build menu, pick any building (even ones grayed out in the base game) and draw it. It appears as a dormant plan. When the research finishes, use the Activate tool (or turn Planning Mode off and Activate) on the plan.

## Save safety
Stores nothing important in the save (just a version integer). You can remove the mod: plans become normal dormant constructions. If you remove it while holding plans for locked rooms, only activate them after the research (without the mod nothing stops early activation).

## Compatibility
Uses Polyphon mixins (no game classes replaced). Hooks: `Lockable.passes`, `CONSTRUCTION.pcreate`, `ConstructionInstance.jobToggle`, `StateManager.activate`.

## Not implemented
- Roads, walls/structures and fortifications behind technology: these aren't rooms but "jobs", with locks checked in many places (each job type, `getPlacable`, lock texts). There's no single clean point to unlock them and still block activation later.
- Free, outdoor rooms with no floor (built instantly by the game, with no planning step) stay locked: planning them would build them instantly.
- A plan's walls, activated one by one with the Activate tool on the wall, can be built (the room itself stays locked).
- The game bug where pasting plans inside a mountain fails (hospital, infirmary, etc.) wasn't reproduced; the mod only fixes the refusal to copy locked rooms.

## Credits
Requires Polyphon (VizardAlpha).
