# SoS Test Bridge

A developer tool for Songs of Syx V71 (0.71.44) that lets a script or an AI coding agent **drive and inspect a running game without a mouse, keyboard or screen**: start a game, build, spawn people, let time pass, read back exactly what happened. It was built to test the other mods in this repository against the real game logic instead of guessing.

> **This is a developer tool, not a gameplay mod.** It can create people, money and buildings, finish construction instantly and call any game function. Don't use it on a save you care about. It does nothing unless you turn it on (see below).

## How it stays safe
- **Off by default.** The bridge only works while the file `%APPDATA%\songsofsyx\bridge\ENABLED` exists. Without it, the mod writes no files and ignores every command. You can create or delete the file while the game runs; it's checked every 5 seconds. Commands written while it was off are dropped, not replayed later.
- **Local files only.** No network port is opened. Commands come from a text file in your own `%APPDATA%` folder, so only programs already running on your PC can use it.
- **Nothing goes into your save.** The bridge stores no data in saves.
- Every command runs on the game's own update thread and calls the same functions the game's buttons call, so mods hooked into those functions react exactly as they would for a player.

## Requirements
- Songs of Syx **V71 (0.71.44)** and the **Polyphon** mod (the bridge uses Polyphon hooks to run in the main menu and world generator).
- Windows PowerShell for the helper scripts. The protocol itself is plain files, so any language can talk to it.

## Quick start
1. Install the mod (build it with `build.ps1 -Polyphon <path>\Polyphon.jar -Deploy`, see the main README) and enable it plus Polyphon in the launcher.
2. Turn it on: create an empty file `%APPDATA%\songsofsyx\bridge\ENABLED` (`start-game.ps1` does this for you).
3. Start the game. `start-game.ps1` launches it without the launcher, minimized and without taking focus, and waits until the bridge answers.
4. Send commands:

```powershell
.\bridge.ps1 "status"
.\bridge.ps1 "newgame"                 # from the main menu
.\bridge.ps1 "load mysave"
.\bridge.ps1 "run 1152 3" -TimeoutSec 300   # let one game day pass at speed 3
.\bridge.ps1 "ascii 380 370 40 20"     # text picture of the map
```

To turn it off, delete the `ENABLED` file.

## Protocol (for any language or AI agent)
All files live in `%APPDATA%\songsofsyx\bridge\`.

| File | Who writes | Content |
| --- | --- | --- |
| `in.txt` | you | One command per line: `<id> <command> <args...>`. Append only; the game reads new lines about 5 times a second. Lines starting with `#` are ignored. |
| `out.jsonl` | game | One JSON line per answer: `{"id":"<id>","ok":true,"result":...}` or `{"id":"<id>","ok":false,"error":"..."}`. |
| `status.json` | game | Rewritten every second while the game runs: screen (`menu`, `worldgen`, `landing`, `settlement`), day, speed, population, whether a popup or question box is open. If it's older than a few seconds, the game is closed or the bridge is off. |
| `ENABLED` | you | Empty file. Its presence turns the bridge on. |

`id` is any token without spaces; use a unique one per command and wait for the line with the same id. Slow commands (like `run`) answer when they finish. Both files are emptied when the game starts.

`bridge.ps1` is a ready-made client: it writes the line, waits for the answer and exits with 0 = ok, 1 = error from the game, 2 = game not running, 3 = timeout.

## Helper scripts
| Script | What it does |
| --- | --- |
| `bridge.ps1 "<command>" [-TimeoutSec n] [-Raw]` | Send one command and print the answer. |
| `start-game.ps1 [-Show] [-Kill] [-Game <folder>]` | Turn the bridge on, start the game minimized (or visible with `-Show`), wait until it answers. `-Kill` closes it. |
| `new-test-game.ps1 [-Race] [-Seed] [-Capital] [-Landing] [-Save]` | Create a new game from the main menu to a running settlement. |
| `reload.ps1 -Save <name> [-Mods a,b]` | Close the game, rebuild and redeploy the listed mods, reopen and load a save. Needs `SOS_POLYPHON` set. |
| `shot.ps1 <file.png>` | Screenshot of the game without showing it on your monitor: the window is restored off-screen without focus, captured and minimized again. |

## Commands
Run `help` and `commands` in game for the live list. Coordinates are tiles.

**Game and time** - `ping`, `status`, `screen`, `speed <0|1|2|3|4|half>`, `run <gameSeconds> [speed]` (lets time pass and answers when done; closes notices that would pause the game), `save <name>`, `saves`, `load <name>`, `dismiss` (the "click to continue" screen after loading), `prompt [yes|no]` (the game's yes/no box), `look <x> <y> [zoom]`, `mouse`, `ui <workprio|rooms|close|build <blueprint>|job <road>>`.

**Main menu, world generator, landing** - `newgame`, `load <name>`, `continue`, `quit`, `wg info|races|race <key>|terrain [seed]|capital <x> <y>|auto|land|mountain|deepmountain|sea|finish`, `landing [x y|auto]`.

**Map and buildings** - `ascii <x> <y> <w> <h>` (text map with legend), `tile <x> <y>`, `rooms`, `room <x> <y>`, `blueprints [filter]`, `place <blueprint> <x> <y> [w h] [item=group,x,y[,rot[,size]]] [door=x,y]` (same path as the build menu), `job <tool|road:key> <x> <y> <w> <h> [overwrite]` (roads, walls, clearing, tunnels...), `plan [on|off]` (planning mode), `finish [x y w h]`, `stock list|alloc`, `work list|prio|target`.

**Research and economy** - `tech list|unlock <key>`, `know`, `research <techKey>`, `res list|add|remove|set`, `credits`, `trade list|export|import|buy|sell|deliver`.

**People** - `humans [race]`, `people`, `groups`, `spawn <race> <type> <n> [x y]`, `kill ...`, `immigrate`, `mood`, `employ`, `workprio`, `path <id>`, `reach <id> <x> <y>`, `tp <id> <x> <y>`.

**World and war** - `faction`, `dip`, `gift`, `region`, `wpath`, `raid`, `army`, `wship`, `vassal`, `caravan`, `gevent`, `invasion`, `div ...` (settlement divisions; `div list enemy` shows invaders), `warmy ...` (world-map armies).

**Escape hatches** - `eval <Class> <staticFieldOrMethod> [args...]` (reads a static field or calls a static method by reflection; args may be strings, numbers, booleans, or `room:x,y`), `debug [global|sett|world] [filter]` and `debug run <panel> <button>` (the game's own developer-panel buttons, e.g. `debug run sett invade small`).

**Companion mods in this repository** (only work when that mod is installed):
- `gate list|set ...` - Species Customs Gate filters.
- `wsf tool|probe|set ...` - Workplace Species Filter per building.
- `fts road [on|off]`, `fts fish <x> <y>`, `fts pillars <blueprint> <x> <y> <w> <h>`, `fts housescan <blueprint> <group> <size> <x1> <y1> <x2> <y2>` - Fixing the Syx: the code behind its keys and clicks, plus a dry-run placement scan.
- `cdstate`, `cdregroup <humanId>` - Cartographer's Dream: island/dock state, boats, invaders, goods lying on the ground.

## Tips for testing with an AI agent
- Compare the same scenario **with and without** the mod you're testing, and count results (people, tiles, goods), instead of reading one case.
- After `load`, send `dismiss`, or time won't pass.
- Count everything that can change: goods delivered with no warehouse are left as piles on the ground, not in storage.
- Keep a dedicated test save; the bridge can change anything.

## Limits
- Tied to the exact game version (V71.44); a game update can break commands until the bridge is updated.
- Keyboard shortcuts and mouse gestures themselves aren't simulated; commands call the code behind them.
- Windows-only helper scripts (the file protocol works anywhere the game runs).

## Credits
Requires Polyphon (VizardAlpha).
