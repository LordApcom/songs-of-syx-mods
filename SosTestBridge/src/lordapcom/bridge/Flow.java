package lordapcom.bridge;

import java.util.Map;

import game.GAME;
import game.faction.FACTIONS;
import game.save.GameLoader;
import init.paths.PATHS;
import init.race.RACES;
import init.race.Race;
import settlement.main.SETT;
import snake2d.CORE;
import snake2d.CORE_STATE;
import view.main.VIEW;
import view.tool.PlacableFixed;
import view.tool.ToolManager;
import vizardalpha.polyphon.api.Shadow;
import world.WORLD;
import world.map.regions.centre.WCentre;
import world.map.regions.centre.WorldCentrePlacablity;

/**
 * Commands for the screens before a settlement exists: main menu, world generator, landing party.
 * Each one drives the same objects the screen's buttons drive (cited per method).
 */
final class Flow {

	/** Last screen object seen by the hooks (menu.Menu, WorldViewGenerator, SettViewStart). */
	static Object menu, gen;

	private Flow() {}

	static Object exec(String screen, String[] a) throws Exception {
		switch (a[0].toLowerCase()) {
		case "newgame": return newgame(screen);
		case "load": return load(screen, a);
		case "continue": return cont(screen);
		case "quit": return quit();
		case "wg": return wg(screen, a);
		case "landing": return landing(screen, a);
		default: return null;
		}
	}

	private static void need(String screen, String want) {
		if (!screen.equals(want))
			throw new IllegalStateException("needs screen '" + want + "', current is '" + screen + "'");
	}

	/** menu/ScRandom.java:79-102 "go": GAME.create(selected scripts) then sandboxSettings.apply(). Menu.start ends in CORE.setCurrentState. */
	private static Object newgame(String screen) {
		need(screen, "menu");
		Object m = menu;
		Object sandbox = Shadow.get(m, "sandboxSettings");
		Shadow.invoke(sandbox, "save");
		CORE.setCurrentState(new CORE_STATE.Constructor() {
			@Override
			public CORE_STATE getState() {
				// forceInit scripts (all test mods) are inserted by the engine regardless of this list
				CORE_STATE s = GAME.create(new String[0]);
				Shadow.invoke(sandbox, "apply");
				return s;
			}
		});
		return Commands.m("started", true, "next", "wait for screen=worldgen, then: wg race <key>");
	}

	/** menu/ScLoad.java:82 menu.start(new GameLoader(path)). */
	private static Object load(String screen, String[] a) {
		need(screen, "menu");
		if (a.length < 2)
			throw new IllegalArgumentException("load <saveName>");
		CORE.setCurrentState(new GameLoader(PATHS.local().save().get(a[1])));
		return Commands.m("loading", a[1]);
	}

	/** menu/ScLoad.java:73 / GameLoader.quickload(): newest save. */
	private static Object cont(String screen) {
		need(screen, "menu");
		return Commands.m("loading", GameLoader.quickload());
	}

	/** menu/ScMain.java:143 exit button. */
	private static Object quit() {
		Bridge.defer(CORE::annihilate);
		return Commands.m("quitting", true);
	}

	private static Object wg(String screen, String[] a) {
		need(screen, "worldgen");
		Object g = gen;
		String sub = a.length > 1 ? a[1].toLowerCase() : "info";
		switch (sub) {
		case "info": {
			Map<String, Object> r = Commands.m(
				"stage", stage(g),
				"race", FACTIONS.player().race().key,
				"seed", WORLD.GEN().seed,
				"terrain", WORLD.GEN().hasGeneratedTerrain,
				"world", Commands.m("w", WORLD.TWIDTH(), "h", WORLD.THEIGHT()),
				"capital", Commands.m("x", WORLD.GEN().playerX, "y", WORLD.GEN().playerY));
			if (a.length >= 4) {
				// same call the capital placer uses (StageCapitol.java:112), with the placer's top-left tile
				int cx = Integer.parseInt(a[2]), cy = Integer.parseInt(a[3]);
				CharSequence p = placer(g) == null ? null : placer(g).placableWhole(cx - WCentre.TILE_DIM / 2, cy - WCentre.TILE_DIM / 2);
				r.put("capitalCheck", p == null ? "ok" : p.toString());
				r.put("vanillaCheck", String.valueOf(WorldCentrePlacablity.terrainC(cx, cy)));
			}
			return r;
		}
		case "races": {
			Map<String, Object> r = Commands.m();
			for (Race race : Lists.of(RACES.playable()))
				r.put(race.key, race.info.name.toString());
			return r;
		}
		case "race": {
			// StagePickRace.java:120 setRace, :103 hasSeletedRace; StageVisuals.java:31 hasProfiled; StagePickTitles.java:72
			if (a.length > 2) {
				Race race = null;
				for (Race r : Lists.of(RACES.all()))
					if (r.key.equalsIgnoreCase(a[2]))
						race = r;
				if (race == null)
					throw new IllegalArgumentException("unknown race " + a[2] + " (wg races)");
				FACTIONS.player().setRace(race);
			}
			Shadow.set(g, "hasSeletedRace", true);
			Shadow.set(g, "hasProfiled", true);
			Shadow.set(g, "hasSelectedTitles", true);
			Shadow.invoke(g, "set");
			return Commands.m("race", FACTIONS.player().race().key, "stage", stage(g));
		}
		case "terrain": {
			// StageTerrain.java:28-43 generate action
			if (a.length > 2)
				WORLD.GEN().seed = Integer.parseInt(a[2]);
			if (WORLD.GEN().playerX != -1)
				Shadow.invoke("view.world.generator.StageCapitol", "clear");
			Shadow.invoke(g, "reset");
			WORLD.TERRAIN().saver().generate(view.world.generator.WorldViewGenerator.loadPrint);
			WORLD.LANDMARKS().saver().generate(view.world.generator.WorldViewGenerator.loadPrint);
			WORLD.MINIMAP().repaint(); // StageTerrain.java:47
			WORLD.GEN().hasGeneratedTerrain = true;
			Shadow.invoke(g, "set");
			return Commands.m("seed", WORLD.GEN().seed, "stage", stage(g));
		}
		case "capital": {
			// The capital tool's own PlacableFixed (StageCapitol.java:88-107), clicked like PlacableFixedTool.click
			if (a.length < 3 || (a.length < 4 && !MODES.contains(a[2])))
				throw new IllegalArgumentException("wg capital <cx> <cy> | wg capital auto");
			PlacableFixed p = placer(g);
			if (p == null || !stage(g).equals("capital"))
				throw new IllegalStateException("capital placer not active (stage=" + stage(g) + ")");
			int cx, cy;
			if (MODES.contains(a[2])) {
				// first spot, scanning from the world centre outwards, that the tool itself accepts;
				// "land" also requires the vanilla rule (no Cartographer relaxation) so the capital sits on normal ground
				int[] c = autoCapital(p, a[2]);
				cx = c[0];
				cy = c[1];
			} else {
				cx = Integer.parseInt(a[2]);
				cy = Integer.parseInt(a[3]);
			}
			CharSequence e = Build.placeItem(p, cx - p.width() / 2, cy - p.height() / 2);
			if (e != null)
				return Commands.m("placed", false, "error", e.toString());
			return Commands.m("placed", true, "capital", Commands.m("x", WORLD.GEN().playerX, "y", WORLD.GEN().playerY), "stage", stage(g));
		}
		case "finish": {
			// StageFinish.java arrow_right clickA: CORE.getInput().clearAllInput() skipped (no input in play)
			if (WORLD.GEN().playerX < 0)
				throw new IllegalStateException("no capital placed");
			GAME.factions().prime();
			WORLD.ARMIES().saver().generate(view.world.generator.WorldViewGenerator.loadPrint);
			WORLD.GEN().isDone = true;
			WORLD.FOW().toggled.set(true);
			VIEW.world().activate();
			VIEW.world().window.centererTile.set(FACTIONS.player().capitolRegion().cx(), FACTIONS.player().capitolRegion().cy());
			GAME.s().CreateFromWorldMap(WORLD.GEN().playerX - 1, WORLD.GEN().playerY - 1, false);
			GAME.saver().saveNew();
			return Commands.m("finished", true, "next", "wait for screen=landing, then: landing <x> <y> | landing auto");
		}
		default:
			throw new IllegalArgumentException("wg info [cx cy] | races | race [key] | terrain [seed] | capital <cx> <cy>|auto|land|mountain|deepmountain|sea | finish");
		}
	}

	/** Landing party: SETT.PLACERS().landingParty clicked like PlacableFixedTool.click (SettViewStart.java:123). */
	private static Object landing(String screen, String[] a) {
		need(screen, "landing");
		PlacableFixed p = SETT.PLACERS().landingParty;
		int cx, cy;
		if (a.length > 1 && a[1].equals("auto")) {
			cx = SETT.TWIDTH / 2;
			cy = SETT.THEIGHT / 2;
			// spiral out from the centre until the vanilla checks accept a spot
			for (int r = 0; r < Math.max(SETT.TWIDTH, SETT.THEIGHT) / 2; r += 4)
				for (int dy = -r; dy <= r; dy += 4)
					for (int dx = -r; dx <= r; dx += 4)
						if (Math.max(Math.abs(dx), Math.abs(dy)) == r && check(p, cx + dx, cy + dy) == null)
							return landAt(p, cx + dx, cy + dy);
			throw new IllegalStateException("no valid landing spot found");
		}
		if (a.length < 3)
			throw new IllegalArgumentException("landing <x> <y> | landing auto");
		return landAt(p, Integer.parseInt(a[1]), Integer.parseInt(a[2]));
	}

	private static CharSequence check(PlacableFixed p, int cx, int cy) {
		p.init(cx, cy);
		int x1 = cx - p.width() / 2, y1 = cy - p.height() / 2;
		if (x1 < 1 || y1 < 1 || x1 + p.width() >= SETT.TWIDTH - 1 || y1 + p.height() >= SETT.THEIGHT - 1)
			return "out of map";
		CharSequence e = p.placableWhole(x1, y1);
		if (e != null)
			return e;
		for (int dy = 0; dy < p.height(); dy++)
			for (int dx = 0; dx < p.width(); dx++)
				if ((e = p.placable(x1 + dx, y1 + dy, dx, dy)) != null)
					return e;
		return null;
	}

	private static Object landAt(PlacableFixed p, int cx, int cy) {
		CharSequence e = Build.placeItem(p, cx - p.width() / 2, cy - p.height() / 2);
		if (e != null)
			return Commands.m("placed", false, "error", e.toString());
		return Commands.m("placed", true, "x", cx, "y", cy, "next", "the view switches to the settlement on the next frame");
	}

	/** mode auto: tool accepts; land: also vanilla accepts; mountain: tool accepts, area touches mountain, not all mountain. */
	private static int[] autoCapital(PlacableFixed p, String mode) {
		int w = WORLD.TWIDTH(), h = WORLD.THEIGHT();
		for (int r = 0; r < Math.max(w, h) / 2; r++)
			for (int dy = -r; dy <= r; dy++)
				for (int dx = -r; dx <= r; dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dy)) != r)
						continue;
					int cx = w / 2 + dx, cy = h / 2 + dy;
					if (p.placableWhole(cx - p.width() / 2, cy - p.height() / 2) != null)
						continue;
					if (mode.equals("land") && WorldCentrePlacablity.terrainC(cx, cy) != null)
						continue;
					if (mode.equals("mountain") && !touchesMountain(cx, cy))
						continue;
					// deepmountain: centre and 7+ of the 9 tiles are mountain; sea: all 9 tiles are ocean
					if (mode.equals("deepmountain") && (!WORLD.MOUNTAIN().is(cx, cy) || count(cx, cy, true) < 7))
						continue;
					if (mode.equals("sea") && count(cx, cy, false) < 9)
						continue;
					return new int[] { cx, cy };
				}
		throw new IllegalStateException("no valid capital spot");
	}

	private static final java.util.List<String> MODES = java.util.List.of("auto", "land", "mountain", "deepmountain", "sea");

	private static int count(int cx, int cy, boolean mountain) {
		int d = WCentre.TILE_DIM / 2, n = 0;
		for (int y = cy - d; y <= cy + d; y++)
			for (int x = cx - d; x <= cx + d; x++)
				if (mountain ? WORLD.MOUNTAIN().is(x, y) : WORLD.WATER().OCEAN.is.is(x, y))
					n++;
		return n;
	}

	private static boolean touchesMountain(int cx, int cy) {
		int d = WCentre.TILE_DIM / 2;
		for (int y = cy - d; y <= cy + d; y++)
			for (int x = cx - d; x <= cx + d; x++)
				if (WORLD.MOUNTAIN().coversTile(x, y))
					return true;
		return false;
	}

	private static PlacableFixed placer(Object g) {
		ToolManager tm = Shadow.get(g, "tools");
		Object pl = Shadow.get(tm.placer, "placer");
		return pl instanceof PlacableFixed ? (PlacableFixed) pl : null;
	}

	/** Mirrors WorldViewGenerator.set() order (view/world/generator/WorldViewGenerator.java:133-150). */
	private static String stage(Object g) {
		boolean race = Shadow.get(g, "hasSeletedRace");
		boolean canRace = Shadow.get(g, "canSelectRace");
		boolean prof = Shadow.get(g, "hasProfiled");
		boolean titles = Shadow.get(g, "hasSelectedTitles");
		if (!race && canRace)
			return "race";
		if (!prof)
			return "visuals";
		if (!titles && FACTIONS.player().titles.unlocked() > 0)
			return "titles";
		if (!WORLD.GEN().hasGeneratedTerrain)
			return "terrain";
		if (WORLD.GEN().playerX < 0)
			return "capital";
		return "finish";
	}
}
