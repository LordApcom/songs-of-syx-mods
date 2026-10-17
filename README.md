# Songs of Syx mods

Six code mods for **Songs of Syx V71 (0.71.44)**, one per folder, plus a developer tool for testing mods with scripts or AI agents. All of them use **Polyphon** (by VizardAlpha) to hook into the game without replacing any game classes, so they work alongside each other and alongside other Polyphon mods.

| Mod | What it does |
| --- | --- |
| [Workplace Species Filter](WorkplaceSpeciesFilter) | Choose, building by building, which species and classes (citizen / slave) may work there and which may use its service. |
| [Plan Ahead](PlanAhead) | Plan any building before its technology is researched. It sits as a dormant plan and can only be activated once the research is done. |
| [Fixing the Syx](FixingTheSyx) | Quality-of-life pack: houses against mountain rock, decoration rows, fishery river fill, roads through mountains, and automatic support pillars. |
| [Cartographer's Dream](CartographersDream) | More dramatic settlement maps and a capital that can be founded almost anywhere (mountain, lake, ocean, islet). A capital surrounded by water stays playable: immigrants, trade and invaders arrive by boat. |
| [Species Customs Gate](SpeciesCustomsGate) | Each gate gets its own species and class filter plus a one-way arrow. Enemies and tourists always pass. |
| [Practice Makes Perfect](PracticeMakesPerfect) | Production gets better with experience instead of research, and fades when the city stops. Research now speeds learning up. |

**Developer tool**

| Tool | What it does |
| --- | --- |
| [SoS Test Bridge](SosTestBridge) | Lets a script or an AI coding agent drive and inspect a running game through local files, with no mouse, keyboard or screen. Off until you opt in. Not a gameplay mod. |

## Requirements
- Songs of Syx **V71 (0.71.44)**.
- The **Polyphon** mod installed and enabled in the launcher, alongside these mods.
- To build: **JDK 21** (`javac` and `jar` on the PATH) and PowerShell.

## Building and installing
Each folder has a `build.ps1`. It needs to know where the game and `Polyphon.jar` are (the latter is inside the `V71\script` folder of the installed Polyphon mod):

```powershell
cd WorkplaceSpeciesFilter
.\build.ps1 -Polyphon "<path>\Polyphon.jar"           # build only, output in dist\
.\build.ps1 -Polyphon "<path>\Polyphon.jar" -Deploy   # build and copy to %APPDATA%\songsofsyx\mods
```

- If the game isn't in the default Steam folder, pass `-Game "<game folder>"`.
- You can also set the `SOS_GAME` and `SOS_POLYPHON` environment variables instead of passing parameters.
- Then enable Polyphon and the mods in the game launcher. On the first launch Polyphon may ask you to restart the game.

## Mod layout
```
<Mod>/
  _Info.txt          name, version and description shown in the launcher
  build.ps1          builds and (with -Deploy) installs the mod
  src/               Java source
  resources/         META-INF/polyphon.json (list of Polyphon hooks)
  assets/            game data the mod replaces (Cartographer's Dream only)
  README.md          what it does, how to use it, compatibility and known limits
```

## Contributing
This repository is public: anyone can download it, build it, fork it and open an issue. Only the repository owner pushes changes and merges pull requests.

## License
[MIT](LICENSE).
