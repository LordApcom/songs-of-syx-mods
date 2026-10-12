# Songs of Syx mods (version 1.x, as published on Steam Workshop)

Four code mods for Songs of Syx, as they were published on Steam Workshop before the 2.0 rewrite. Each folder is a Maven project (`pom.xml`) that builds against `SongsOfSyx.jar` from the game folder.

| Mod | Notes |
| --- | --- |
| Workplace Species Filter | Per-building species and class filters for workers and service users. |
| Plan Ahead | Place any building before researching its technology. |
| Fixing the Syx | Quality-of-life fixes. |
| Cartographer's Dream | Settlement map generation overhaul and capitals almost anywhere. |

Workplace Species Filter and Cartographer's Dream worked by replacing some of the game's own classes with edited copies. Those copies are the game's code and are not included; each of those two folders lists them in `GAME-CLASSES.md`, so these two don't build from this repository as is.

Version 2.0 rewrites all of them on Polyphon hooks (no game classes replaced) and adds Species Customs Gate.

## License
[MIT](LICENSE), for the mod code in this repository.
