# Fixing the Syx

Quality-of-life pack for Songs of Syx (V71, 0.71.44). Five independent features with no save data, so it's safe to add or remove mid-campaign. Uses Polyphon mixins and replaces no game classes.

Requires Polyphon (VizardAlpha).

## Installation
See the installation section in the repository's main README.

## How to use

1. **Houses on mountain rock.** An indoor building (houses, etc.) whose door faces rock is no longer rejected as "blocked". Construction digs out the entrance on its own. This only applies when the rock in front of the door is one tile thick (open ground on the other side); a door facing deeper rock stays blocked, so builders don't get sealed inside.
2. **Decoration rows** (Decorations category only, single-item tool):
   - Ctrl+Alt+wheel: count (1 to 64).
   - Ctrl+Shift+wheel: spacing in tiles, center to center.
   - R: rotates the item, as in the base game, and turns the row with it (east, south, west, north), with the mouse over the map.
   - Click: places the row. Translucent ghosts preview the copies, and "Row xN, step S" appears in the info box.
   - Default spacing: monument family (torches, etc.) = 2 x radius - 1; other decorations = item width.
3. **Fishery river fill.** With the fishery tool, Ctrl+click a shallow-water tile to fill the connected water, nearest first, respecting the room's maximum size and dimensions (counting what's already painted) and skipping tiles owned by other rooms. You still confirm the room and place the items.
4. **Roads through mountains.** Drag or click with the road tool (any type) over rock: on release, every mountain tile gets "dig, then road". Once a tile is dug out, the road is queued. Erasing the queued tunnel frees the tile. Press T while a road tool is active to toggle this (on by default each time the game starts). On: mountain tiles show as valid (green); off: red, as in the base game.
5. **Support pillars.** While building an indoor room by area, Ctrl+click a yellow tile: the mod plans a symmetric grid centered on the room and removes one cluster at a time (about 0.5 s apart), only where yellow tiles remain nearby and without splitting the room. Pasture inside a mountain: 1x1 pillars on an 8-tile grid. Everything else: "+"-shaped clusters on an 8-tile grid. Symmetry wins over completeness, so a few yellow tiles may remain.

## Compatibility
- No save data. Works alongside other mods; uses only Polyphon hooks (ModifyReturn and Inject).
- If a hook fails, the error is logged once in the game log (`[FixingTheSyx]`) and the vanilla behavior continues.

## Known limits
- Roads: the "dig, then road" list lives only in memory. If you reload the save before digging finishes, the tunnel keeps going but the road has to be placed again.
- Roads: with T on, every rock tile in the selection shows green, not just the tile under the cursor; there's no "anti" icon.
- Decoration rows: the radius used is the monument's base radius for the item size (the game code doesn't vary radius by upgrade level). Non-monument decorations use the item width.
- Rows: copies that don't fit are skipped and the rest are placed.
- Pillars: the grid center is the middle of the bounding box, so in even-width rooms symmetry is off by half a tile. Clusters touching items already placed are skipped.
- Fishery: on very long rivers, only the stretch nearest the click (up to the room limit) is filled.

## Not implemented
Nothing from the original list was cut, but the two differences above (green only under the cursor with an "anti" icon, and radius by upgrade level) are approximations.
