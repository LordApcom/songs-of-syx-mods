# Practice Makes Perfect

Production gets better with experience instead of research, and fades when the city stops. No game class is replaced: everything is done with Polyphon hooks.

> Not tested in long games yet. It may have bugs, and it isn't known yet whether it's safe to add to a game in progress or remove mid-game. Back up your save.

## How it works
- Every production boost that the techs of **Agriculture, Husbandry, Mining, Refining and Workshops** gave to a room's output (`ROOM_FARM_GRAIN`, `ROOM_MINE_ORE`, `ROOM_WORKSHOP_CARPENTER`...) is taken off the techs. In its place each of those 37 production types gets one **Experience** booster, worth up to what all those tech levels gave together.
- Each game day a type that has workers closes 1/320 of the gap to full experience (20 years of 16 days): about 5% after 1 year, 22% after 5, 39% after 10, 63% after 20. Other types in the same tree learn at a third of that pace. When the whole tree has no workers, experience fades at half the pace, in proportion to what is left.
- Researched levels of the old production techs speed learning up, to 3x with every level researched. Each of those techs says so on the tech screen.
- Tool techs (`EQUIP_LEVEL_TOOL_*`), input-saving techs (`ROOM_CONSUMPTION_*`) and unlocks are untouched.
- NPC factions learn every type on the same curve from day one, as if always busy, without research.
- Experience is stored in the save. Saved without the mod = everyone starts at zero.

## Compatibility
- Requires **Polyphon** (>= 0.2.0). One hook, on `BOOSTING.finishSetup`: it queues the swap after the techs read their boosts and before the game wires them.
- Mods that change the same production techs may not combine well.

## Not implemented
- Experience per building or per worker: it is per production type, for the whole city.
- An experience overview screen: the value shows in each room's production breakdown.

## Credits
Requires Polyphon (VizardAlpha).
