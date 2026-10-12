# Species Customs Gate (rewrite for V71.44 on Polyphon)

Each gate (room `GATEHOUSE`) gets its own **species x class (citizen / slave)** filter and an **arrow** that can be flipped. No game class is replaced: everything is done with Polyphon hooks.

## How to use
- **Right-click** a gate tile (or press **F** with the mouse over the gate) to open the filter panel. Left-click still locks/unlocks, as in the base game.
- Panel: a species grid (citizen row, slave row), shortcuts **Allow all / Citizens only / Slaves only / Block all**, an **Invert arrow** button and **Apply**.
- The gate tooltip shows `Everyone`, `NONE allowed` or `Allowed (x/y): list`, plus the arrow direction.
- Permanent yellow arrows appear over every gate tile.
- Everything is stored in the save (per gate: anchor, flipped arrow, bits). Saved without the mod = open gates; saved with the mod and loaded without it = the game ignores the block.

## Passage rules
- Anyone who **is allowed** passes both ways.
- Anyone who **is not allowed** can only enter from the back side of the arrow, moving straight, and exit through the front. Against the arrow or from the sides, the gate acts as a wall for that person.
- **Hostiles, rioters, tourists and soldiers** always pass (soldiers: a design choice, otherwise defense breaks). Nobles, children and prisoners (no filterable species/class) also pass.
- Inside the gate corridor, movement is free; anyone who ends up inside (teleport, filter changed) is never trapped.

## Warning: arrow pointing inward
Someone who is not allowed and is on the inner side of an arrow pointing inward **can't get out**, not even to eat or sleep (work plans may cross the arrow). This is by design: the arrow really holds, so put food and housing inside an enclosure set up this way.

## How people getting stuck was avoided (by design)
1. **Decide when the plan is chosen**: the service/consumption finder doesn't offer a target the humanoid can reach but can't come back from (leisure). Work plans may cross the arrow.
2. **Three-state reachability** (`CONNECTED / DISCONNECTED / BUDGET_EXHAUSTED`): only a proven "DISCONNECTED" blocks; running out of budget in a tick = allow and try again later. Only targets reachable *without* gates but not *with* them count as disconnected (regions isolated for other reasons aren't the gate's fault).
3. Per-species region map: the map is flood-filled treating restricted gates as walls; each gate becomes a one-way edge between regions (the arrow). Reused by the API.
4. Per-session state: `InstanceState` (per game and per humanoid), nothing static that survives between games. Cleaned up when a humanoid is removed and when its plan changes.
5. Minimal safety net: 8 bumps into a gate within ~4 s -> the plan is interrupted, the target is forgotten for ~30 s in service searches and, the 3rd time within ~2 min, the humanoid loses its job.
6. "Any tile" SFINDER searches (storage, resources, jobs) are deliberately **unfiltered**: filtering them made haulers stop.

## API for other mods
`lordapcom.gate.GateApi.canReach(Humanoid h, int tx, int ty)` -> `true` if the mod is inactive, h is hostile/a tourist, the species is allowed at every gate, the target is reachable respecting filters and arrows, or the budget ran out. `false` only when proven disconnected. (Workplace Species Filter already calls it via reflection.)

## Compatibility
- Requires **Polyphon** (>= 0.2.0). Hooks on `ResolverTile.collides`, `Humanoid.collideTile`/`removeAction`, `AIModules.getNextPlan`, `SFinderRoomService.getReservable`. `getNextPlan` and `getReservable` are also used by Workplace Species Filter; Polyphon chains the hooks, and this one only adds restrictions (it never allows what the other blocked).
- Replaces no game class.

## Not implemented
- **Routing around the gate**: the game's pathfinder works per tile (and runs on its own thread, with a component graph), with no hook point that knows the humanoid. Anyone who still picks a path through a forbidden gate is stopped by collision and falls into the safety net. As a result, the "enter and exit only in a straight step" rule applies at collision (they can only enter through the entry layer, coming from behind the arrow), not in route planning.
- **Cargo carts and caravans** aren't `Humanoid` (they're half-entities) and don't go through filtered collision: they cross freely both ways, more permissive than "with the arrow only".
- **Home plan** (`SFinderHome.findHome`): not filtered. Refusing the home unassigns the housing, which is worse than the problem.
- Service choices made when *resuming* a plan (outside `getNextPlan`) aren't filtered, only those of a new plan.
- `SFinderWater` and `SFinderIndoors` aren't filtered.
- Per-citizen diagnostic panel.

## Credits
Requires Polyphon (VizardAlpha).
