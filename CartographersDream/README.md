# Cartographer's Dream

Code mod for Songs of Syx V71 (0.71.44). Requires Polyphon (VizardAlpha).

## What it does
1. **More dramatic settlement maps.** Increases lakes, islands inside lakes, rivers, mountain ranges, and the number/size/tunnels of caves in settlement map generation (data file `GenerationSettlement.txt`).
2. **A capital almost anywhere.** On the found-your-capital screen, the only site rejected is one where all 9 tiles (3x3) are mountain. Mountain, lake, ocean, islet and wide river are all allowed.
3. **A capital surrounded by water stays playable.**
   - If the capital starts (or ends up) with no land road and the throne touches open sea, immigrants, caravans, returning troops, tourists and bandits use a **dock** (a coastal tile of the island). People leaving (emigrants, caravans, hunters, emissaries) walk to the dock and leave from there.
   - On the world map, if the path graph leaves the capital isolated, the mod links it to the 2 nearest regions (trade, armies, garrisons and bandits work). It only adds a link when it detects isolation. It doesn't regenerate the world map.
   - A capital in the ocean or on a mountain starts with an irregular island/plateau about 30 tiles in radius, cleared around the landing point (otherwise there'd be nowhere to land). For a sea capital, a 6-tile band along the map edge becomes deep water (shallow water within 2 tiles of land stays). Regular land maps are untouched.
   - The game's "Capital Closed!" warning still appears if you wall in your own throne: the mod only helps when the capital was founded cut off by the sea. During an invasion the game shows that warning on every map, island or not.
   - Attempted fix for the known "hunters don't work in an empty ocean capital" problem: while the capital is an island, the game finds a "way out" (the dock).
   - **Arrival by boat.** On an island capital, arrivals come in the game's fishing boat, standing in it with their own species' look: immigrants, tourists and returning troops set out from the map edge by sea and only appear on land when the boat docks. Merchant caravans come in a boat with their goods and continue on foot after landing. Invaders that spawn at sea, or on land across the sea, are ferried to the docks, land there and march on the throne. Several boats leaving from the same spot set out in rows.

## How to use
Nothing to configure. Enable the mod when **starting a new campaign** and found the capital wherever you like. To see the different maps, generate a new world.

## Important: campaign start only
Only add the mod when starting a campaign. The world-graph links are saved in the save (the game without the mod simply ignores them), but an island capital depends on the mod for immigration and trade. Removing it mid-game sends an island capital back to "Capital Closed!". The mod saves only a version number and an "island capital" flag, and loads saves without that data without errors.

## Compatibility
- Uses Polyphon mixins and replaces no game classes. Hooks: `WorldCentrePlacablity.terrain`, `world.map.pathing.Gen.generateAll`, `Initer.markArround`, `EntryPoints.update` and `randomReachable(int)`, `SFinderEntry.find/any/anyHas`, `ENTETIES.add`, `Humanoid.update`, `Caravan.init/update/render`, `HalfEnts.renderBelow`.
- The file `assets/init/config/GenerationSettlement.txt` replaces the entire `GENERATION` block. Another mod that also replaces this file wins or loses depending on load order.
- **Heroes of Syx** (v2.14.2 or newer): load this mod **after** it.
- Failures inside hooks are caught and logged once; the game falls back to normal behavior.
- Goods bought for an island capital are dropped near the throne, the same as on land when there's no warehouse to take them.

## Not implemented
- **Boat trips aren't saved.** If you save with boats mid-trip, on load the passenger is already at the landing point (or, for an invader, where it spawned). Trips start at most 80 tiles from the coast so nobody waits minutes on a large sea map.
- **New landforms (peninsulas, fjords, canyons, valleys).** The settlement generator only exposes intensities (lake size, river width, etc.); the mod tunes those values. Peninsulas and fjords depend on world-map terrain, which the mod doesn't regenerate.
- **Fix for old saves.** The world-map link runs only during world generation (new campaign).
- Larger maps: out of scope.

## Known limits
- Returning troops use the same path as immigrants (dock + boat), but weren't tested in game on their own.

## Credits
Requires Polyphon (VizardAlpha).
