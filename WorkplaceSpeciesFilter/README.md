# Workplace Species Filter (V71.44)

Code mod for Songs of Syx. Gives **per-building** control over who may work there and who may use its service.

## What it does
Two independent filters per building, each with toggles by **species** and by **class** (citizen / slave):

1. **Workers**: who may take a job in the building (any workplace).
2. **Users**: who may use the service (baths, temples, shrines, stages, arenas, canteens, taverns, hospitals, etc.; any room that provides a service).

It's a **hard block**: a species/class that isn't allowed is never hired at that building and never uses that service (the service disappears from its searches). The game's native soft preference (work groups in the priority panel) keeps working alongside the filter.

## How to use
1. In the bottom bar, click the **Tools** button (gear). The mod adds a **Species filter** entry as another cell in the tools grid (after the last tool), in the same style as the others.
2. Clicking it opens the **Species filter** tool. The orange/blue header at the top shows the current mode: **SETTING: WORKERS** (who may work) or **SETTING: USERS** (who may use the service). Switch with the Workers / Users buttons; each mode keeps its own selection.
3. Check the **allowed** species/classes (checked buttons = allowed; shortcuts: everyone, all citizens, all slaves, nobody).
4. Click or drag over buildings. A selection with everything checked removes the filter.
5. While the tool is active, **every workplace (hammer) and every service building (heart)** gets an outline along its edges and a badge in the center: **?** = no filter (everyone); a class icon = citizens only or slaves only; species icons = only those; crossed-out icons = everyone **except** those; cancel = nobody. Orange outline and hammer = workers filter, blue outline and heart = users filter; the current mode is drawn bright and the other dimmed. Buildings that are both show both badges stacked.

When you restrict a building's workers, anyone already working there who isn't allowed is fired immediately.

## Persistence and compatibility
- Saved with the save. Old saves or saves without the mod have no data: no filter (everyone allowed).
- The filter is tied to the building instance; demolishing the building removes the filter. It doesn't survive rebuilding.
- The mod is always on (it doesn't appear in the new-game script list). Removing it from a save that used it triggers the game's normal missing-script warning.
- Requires **Polyphon** (VizardAlpha). Replaces no game classes; it only injects at the points listed below. May conflict with mods that change hiring (`StatsWork`/`RoomEmployments`) or service search (`SFinderRoomService`, `AIModules`).
- Optional integration with the Species Customs Gate mod: if the class `lordapcom.gate.GateApi` exists (checked once via reflection), a worker assignment is refused when `GateApi.canReach(humanoid, x, y)` returns false. Without that mod, nothing happens.

## How it works (summary)
- Hiring: the central employment setter (`StatsWork$Work.set`) refuses the assignment; the instance draw (`RoomEmployments$RaceGroup.setWork`) skips buildings that don't accept the humanoid; `EmployerSimple.employ` (guards, trainers, university, asylum) reports failure if it was refused.
- Allocation: the game hands out job slots per building type, not per building. While its allocator runs (`Employer.update`), a species stops getting slots in a type once it holds as many as the active buildings that accept it can take, and any excess is handed back, so the rest go to the next species in line. The priority screen always shows and edits your real values.
- Service: `AIModules.getNextPlan` marks who is choosing the plan; `SFinderRoomService.getReservable` hides service tiles of rooms that exclude that person.

## Known limits
- Swapping jobs between two workers can leave a room 1 person over target for a moment if one side is refused; the game corrects it on its own.
- Anyone already on their way to the service when the users filter changes finishes the trip.

## Not implemented
- Nothing from the concept was left out. The entry lives in the bottom bar's Tools menu (no dedicated hotkey or tool search).

## Credits
Requires Polyphon (VizardAlpha).
