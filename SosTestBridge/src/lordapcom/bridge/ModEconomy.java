package lordapcom.bridge;

import init.resources.RBIT;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import game.boosting.BSourceInfo;
import game.boosting.BValue;
import game.boosting.BoosterValue;
import game.faction.FACTIONS;
import game.faction.FCredits;
import game.faction.FCredits.CTYPE;
import game.faction.npc.FactionNPC;
import game.faction.player.PTech.TechCurr;
import game.faction.player.Player;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.sprite.UI.UI;
import init.tech.TECH;
import init.tech.TECH.TechRequirement;
import init.tech.TECHS;
import init.tech.TechCost;
import settlement.job.Job;
import settlement.main.SETT;
import settlement.misc.util.TILE_STORAGE;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomInstance;
import settlement.room.main.employment.RoomEmployment;
import settlement.room.main.job.StorageCrate;
import settlement.tilemap.terrain.Terrain.TerrainTile;
import snake2d.util.datatypes.COORDINATE;

/** Economy / research / construction commands. */
final class ModEconomy {

	static void register() {
		Registry.add("res", "res list | res add|remove|set <key> <n> [x y]  (add fills stockpile crates, remainder is dropped at x y, default first stockpile)", ModEconomy::res);
		Registry.add("credits", "credits | credits add|remove|set <n>", ModEconomy::credits);
		Registry.add("know", "know | know add <currencyKey|all> <n>  (tech knowledge; booster lasts for the session only)", ModEconomy::know);
		Registry.add("research", "research <techKey> [max]  (gives missing knowledge, then unlocks through the UI path incl. prerequisites)", ModEconomy::research);
		Registry.add("finish", "finish [x y w h]  (no args: every construction site; with area: also finishes jobs like walls/roads)", ModEconomy::finish);
		Registry.add("stock", "stock list | stock alloc <x> <y> <resKey> <crates>  (stockpile crate allocation per resource)", ModEconomy::stock);
		Registry.add("work", "work list | work prio <roomKey> <0-30> | work target <roomKey> <n|max>  (workplace priority / workers per workplace type)", ModEconomy::work);
	}

	private static RESOURCE resOf(String k) {
		for (RESOURCE r : Lists.of(RESOURCES.ALL()))
			if (r.key.equalsIgnoreCase(k))
				return r;
		throw new IllegalArgumentException("unknown resource " + k + " (res list)");
	}

	// ---- resources ----------------------------------------------------------------------------

	// game path: settlement/thing/ThingPlacer.java:90 'increase crate' (reserve+deposit) and :43 'resources all' (resources.createPrecise); removal: init/resources/RESOURCE.java:496 remove(total, record)
	private static Object res(String[] a) {
		if (a.length < 2 || a[1].equals("list")) {
			List<Object> l = new ArrayList<>();
			for (RESOURCE r : Lists.of(RESOURCES.ALL()))
				l.add(Commands.m("key", r.key, "name", r.name.toString(), "owned", r.owned(), "stockpiled",
						SETT.ROOMS().STOCKPILE.tally().amountTotal(r)));
			return l;
		}
		if (a.length < 4)
			throw new IllegalArgumentException("res list | res add|remove|set <key> <n> [x y]");
		RESOURCE r = resOf(a[2]);
		int n = Commands.i(a, 3);
		int delta = n;
		if (a[1].equals("set"))
			delta = n - r.owned();
		else if (a[1].equals("remove"))
			delta = -n;
		else if (!a[1].equals("add"))
			throw new IllegalArgumentException("res list | res add|remove|set <key> <n> [x y]");
		int done = 0;
		if (delta < 0) {
			done = -r.remove(-delta, null);
		} else if (delta > 0) {
			int rem = delta;
			for (StockpileInstance ins : Lists.of(SETT.ROOMS().STOCKPILE.all())) {
				for (COORDINATE c : ins.body()) {
					if (rem <= 0 || !ins.is(c))
						continue;
					StorageCrate cr = ins.crate(c.x(), c.y());
					if (cr == null || cr.resource() != r)
						continue;
					int k = Math.min(cr.storageReservable(), rem);
					if (k > 0) {
						cr.storageReserve(k);
						cr.storageDeposit(k);
						rem -= k;
					}
				}
			}
			int crates = delta - rem;
			if (rem > 0) {
				int x, y;
				if (a.length > 5) {
					x = Commands.i(a, 4);
					y = Commands.i(a, 5);
				} else if (SETT.ROOMS().STOCKPILE.instancesSize() > 0) {
					x = SETT.ROOMS().STOCKPILE.getInstance(0).mX();
					y = SETT.ROOMS().STOCKPILE.getInstance(0).mY();
				} else
					throw new IllegalStateException("no stockpile crate with " + r.key + " and no stockpile; give x y to drop the rest on the ground");
				SETT.THINGS().resources.create(x, y, r, rem);
			}
			return Commands.m("key", r.key, "intoCrates", crates, "dropped", rem, "owned", r.owned());
		}
		return Commands.m("key", r.key, "changed", done, "owned", r.owned());
	}

	// ---- credits ------------------------------------------------------------------------------

	// game path: game/faction/player/Player.java:68 'add credits' -> PCredits.inc(n, CTYPE.MISC)
	private static Object credits(String[] a) {
		FCredits c = FACTIONS.player().credits();
		if (a.length > 1) {
			if (a.length < 3)
				throw new IllegalArgumentException("credits | credits add|remove|set <n>");
			int n = Commands.i(a, 2);
			switch (a[1]) {
			case "add": c.inc(n, CTYPE.MISC); break;
			case "remove": c.inc(-n, CTYPE.MISC); break;
			case "set": c.inc(n - c.getD(), CTYPE.MISC); break;
			default: throw new IllegalArgumentException("credits | credits add|remove|set <n>");
			}
		}
		return Commands.m("credits", c.getD());
	}

	// ---- knowledge ----------------------------------------------------------------------------

	// game path: game/faction/player/PTech.java:82 'unlockRooms' (BoosterValue added to the currency's boostable)
	private static Object know(String[] a) {
		if (a.length > 1) {
			if (!a[1].equals("add") || a.length < 4)
				throw new IllegalArgumentException("know | know add <currencyKey|all> <n>");
			int n = Commands.i(a, 3);
			boolean any = false;
			for (TechCurr c : Lists.of(FACTIONS.player().tech().currs())) {
				if (a[2].equals("all") || c.cu.bo.key.equalsIgnoreCase(a[2])) {
					addKnowledge(c, n);
					any = true;
				}
			}
			if (!any)
				throw new IllegalArgumentException("unknown currency " + a[2] + " (know)");
		}
		List<Object> l = new ArrayList<>();
		for (TechCurr c : Lists.of(FACTIONS.player().tech().currs()))
			l.add(Commands.m("key", c.cu.bo.key, "total", c.total(), "allocated", c.allocated(), "frozen", c.frozen(), "available", c.available()));
		return l;
	}

	private static void addKnowledge(TechCurr c, double n) {
		BValue v = new BValue.BValuePlayerOnly() {
			@Override
			public double vGet(Player f) {
				return 1;
			}

			@Override
			public double vGet(FactionNPC f) {
				return 0;
			}
		};
		new BoosterValue(v, new BSourceInfo("bridge", UI.icons().s.expand), n, false).add(c.cu.bo);
	}

	// game path: view/ui/tech/Prompt.java:37 (canUnlockNext gate) + :94 pUnlock recursion -> PTech.levelSet; funding mirrors the unlockRooms cheat
	private static Object research(String[] a) {
		if (a.length < 2)
			throw new IllegalArgumentException("research <techKey> [max]");
		TECH t = null;
		for (TECH x : Lists.of(TECHS.ALL()))
			if (x.key.equalsIgnoreCase(a[1]))
				t = x;
		if (t == null)
			throw new IllegalArgumentException("unknown tech " + a[1] + " (tech list)");
		boolean max = a.length > 2 && a[2].equals("max");
		var pt = FACTIONS.player().tech();
		List<String> done = new ArrayList<>();
		do {
			if (pt.level(t) >= t.levelMax)
				break;
			researchOne(t, pt.level(t) + 1, done);
		} while (max);
		return Commands.m("key", t.key, "level", pt.level(t), "max", t.levelMax, "researched", done);
	}

	/**
	 * Like a player clicking research: prerequisites first (each researched the same way), then pay the knowledge
	 * and unlock one level through the tech prompt's checks (view/ui/tech/Prompt.java:37 canUnlockNext, :94 pUnlock).
	 */
	private static void researchOne(TECH t, int level, List<String> done) {
		var pt = FACTIONS.player().tech();
		for (int i = 0; i < t.requires().size(); i++) {
			TechRequirement r = t.requires().get(i);
			while (pt.level(r.tech) < r.level)
				researchOne(r.tech, pt.level(r.tech) + 1, done);
		}
		while (pt.level(t) < level) {
			for (TechCurr c : Lists.of(pt.currs())) {
				double amt = 0;
				for (TechCost k : t.costs)
					if (k.cu == c.cu)
						amt += k.amount;
				int need = pt.costOfNextWithRequired(c.cu, amt, t) - Math.max(0, c.available());
				if (need > 0)
					addKnowledge(c, need);
			}
			if (!pt.canUnlockNext(t)) {
				// name the game requirement that blocks it (e.g. POPULATION > 500), like the tech hover does
				StringBuilder why = new StringBuilder();
				for (init.value.Lock<?> l : Lists.of(t.requires.all()))
					if (!((init.value.Lock<Object>) l).unlocker.inUnlocked(null))
						why.append(" ").append(l.unlocker.name);
				throw new IllegalStateException("cannot unlock " + t.key + ": requirement not met:" + (why.length() > 0 ? why : " (cost)"));
			}
			unlock(t, pt.level(t) + 1);
			done.add(t.key + "=" + pt.level(t));
		}
	}

	private static void unlock(TECH t, int level) {
		for (TechRequirement r : t.requires())
			if (FACTIONS.player().tech().level(r.tech) < r.level)
				unlock(r.tech, r.level);
		FACTIONS.player().tech().levelSet(t, level);
	}

	// ---- instant construction -----------------------------------------------------------------

	private static Method construct;
	private static Object constructBp;

	// game path: settlement/room/main/construction/ConstructionBlueprint.java:39 'Finish room' debug placer (construct(tx,ty), else job.becomes + clearer + placeFixed)
	private static Object finish(String[] a) throws Exception {
		if (construct == null) {
			Field f = SETT.ROOMS().construction.getClass().getDeclaredField("construction");
			f.setAccessible(true);
			constructBp = f.get(SETT.ROOMS().construction);
			construct = constructBp.getClass().getMethod("construct", int.class, int.class);
			construct.setAccessible(true);
		}
		boolean area = a.length > 1;
		int x1 = 0, y1 = 0, x2 = SETT.TWIDTH, y2 = SETT.THEIGHT;
		if (area) {
			x1 = Commands.i(a, 1);
			y1 = Commands.i(a, 2);
			x2 = x1 + Commands.i(a, 3);
			y2 = y1 + Commands.i(a, 4);
		}
		int rooms = 0, jobs = 0, dormant = 0;
		// Construction sites first; remember their tiles: the walls/roof of an indoor room are separate jobs on and
		// around those tiles, and the room is not "built with walls" until those jobs are done too.
		List<int[]> siteTiles = new ArrayList<>();
		for (int y = Math.max(0, y1); y < Math.min(SETT.THEIGHT, y2); y++)
			for (int x = Math.max(0, x1); x < Math.min(SETT.TWIDTH, x2); x++)
				if (SETT.ROOMS().construction.isser.is(x, y)) {
					// a dormant plan (planning mode / Plan Ahead) gets no builders in the game, so it is not finished either
					if (SETT.ROOMS().map.get(x, y) instanceof settlement.job.ROOM_JOBBER j && !j.isJobActive()) {
						dormant++;
						continue;
					}
					siteTiles.add(new int[] { x, y });
				}
		// walls first, as builders do (the room is finished inside its walls), then the room, then whatever is left
		for (int[] t : siteTiles)
			for (int dy = -1; dy <= 1; dy++)
				for (int dx = -1; dx <= 1; dx++)
					if (!SETT.ROOMS().construction.isser.is(t[0] + dx, t[1] + dy) && completeJob(t[0] + dx, t[1] + dy))
						jobs++;
		for (int[] t : siteTiles) {
			if (SETT.ROOMS().construction.isser.is(t[0], t[1])) {
				construct.invoke(constructBp, t[0], t[1]);
				rooms++;
			}
		}
		for (int[] t : siteTiles)
			for (int dy = -1; dy <= 1; dy++)
				for (int dx = -1; dx <= 1; dx++)
					if (completeJob(t[0] + dx, t[1] + dy))
						jobs++;
		if (area)
			for (int y = Math.max(0, y1); y < Math.min(SETT.THEIGHT, y2); y++)
				for (int x = Math.max(0, x1); x < Math.min(SETT.TWIDTH, x2); x++)
					if (completeJob(x, y))
						jobs++;
		return Commands.m("constructionTiles", rooms, "jobs", jobs, "skippedDormantTiles", dormant);
	}

	/**
	 * game path: settlement/job/Debug.java "reservePerform" debug tool: reserve, then jobPerform(null, res, 1),
	 * repeated until the job is gone (multi-step jobs such as dig-then-road or wall-then-roof need several rounds).
	 */
	private static boolean completeJob(int x, int y) {
		if (!SETT.IN_BOUNDS(x, y))
			return false;
		boolean did = false;
		for (int k = 0; k < 64; k++) {
			Job j = SETT.JOBS().getter.get(x, y);
			if (j == null)
				break;
			RESOURCE res = null;
			RBIT bb = j.jobResourceBitToFetch();
			if (bb != null)
				for (RESOURCE r : Lists.of(RESOURCES.ALL()))
					if (bb.has(r)) {
						res = r;
						break;
					}
			if (j.jobReserveCanBe())
				j.jobReserve(res);
			else if (j.jobReservedIs(res))
				j.jobPerform(null, res, 1);
			else
				break;
			did = true;
		}
		return did;
	}

	// ---- stockpile allocation -----------------------------------------------------------------

	// game path: settlement/room/infra/stockpile/Gui.java:478/486 +/- buttons -> StockpileInstance.allocateCrate (package-private, via reflection)
	private static Object stock(String[] a) throws Exception {
		if (a.length < 2 || a[1].equals("list")) {
			List<Object> l = new ArrayList<>();
			for (StockpileInstance ins : Lists.of(SETT.ROOMS().STOCKPILE.all())) {
				Map<String, Object> alloc = Commands.m();
				for (RESOURCE r : Lists.of(RESOURCES.ALL())) {
					int c = SETT.ROOMS().STOCKPILE.tally().crates.get(r, ins);
					if (c > 0)
						alloc.put(r.key, c);
				}
				l.add(Commands.m("x", ins.mX(), "y", ins.mY(), "crates", ins.totalCrates(), "alloc", alloc));
			}
			return l;
		}
		if (!a[1].equals("alloc") || a.length < 6)
			throw new IllegalArgumentException("stock list | stock alloc <x> <y> <resKey> <crates>");
		StockpileInstance ins = SETT.ROOMS().STOCKPILE.getter.get(Commands.i(a, 2), Commands.i(a, 3));
		if (ins == null)
			throw new IllegalArgumentException("no stockpile at " + a[2] + "," + a[3]);
		RESOURCE r = resOf(a[4]);
		Method m = StockpileInstance.class.getDeclaredMethod("allocateCrate", RESOURCE.class, int.class);
		m.setAccessible(true);
		m.invoke(ins, r, Commands.i(a, 5));
		return Commands.m("key", r.key, "crates", SETT.ROOMS().STOCKPILE.tally().crates.get(r, ins));
	}

	// ---- work priorities / worker allocation --------------------------------------------------

	// game path: view/sett/ui/room/priority/Table.java:39 (RoomEmployment.priority) and view/sett/ui/room/ModuleEmployment.java:205 (employees().neededSet)
	private static Object work(String[] a) {
		if (a.length < 2 || a[1].equals("list")) {
			List<Object> l = new ArrayList<>();
			for (RoomEmployment e : Lists.of(SETT.ROOMS().employment.ALL()))
				l.add(Commands.m("key", e.blueprint().key(), "instances", e.blueprint().instancesSize(), "priority", e.priority.get(),
						"employed", e.employed(), "needed", e.neededWorkers()));
			return l;
		}
		// game path: view/sett/ui/room/priority/UIRoomRaceAssign.java (employees().prefferedSet)
		if (a[1].equals("prefer") && a.length >= 6) {
			RoomInstance ins = (RoomInstance) SETT.ROOMS().map.instance.get(Commands.i(a, 2), Commands.i(a, 3));
			init.type.WGROUP.HTypeBitsImp t = new init.type.WGROUP.HTypeBitsImp(false);
			List<String> set = new ArrayList<>();
			for (init.type.WGROUP g : Lists.of(init.type.WGROUP.all()))
				if (g.race.key.equalsIgnoreCase(a[4]) && g.type.CLASS.key.equalsIgnoreCase(a[5])) {
					t.set(g);
					set.add(g.race.key + "/" + g.type.key);
				}
			ins.employees().prefferedSet(t);
			return Commands.m("room", ins.blueprint().key, "preferred", set);
		}
		if (a.length < 4)
			throw new IllegalArgumentException("work list | work prio <roomKey> <0-30> | work target <roomKey> <n|max> | work prefer <x> <y> <bits>");
		RoomEmployment emp = null;
		for (RoomEmployment e : Lists.of(SETT.ROOMS().employment.ALL()))
			if (e.blueprint().key().equalsIgnoreCase(a[2]))
				emp = e;
		if (emp == null)
			throw new IllegalArgumentException("unknown workplace " + a[2] + " (work list)");
		if (a[1].equals("prio")) {
			emp.priority.set(Commands.i(a, 3));
			return Commands.m("key", a[2], "priority", emp.priority.get());
		}
		if (!a[1].equals("target"))
			throw new IllegalArgumentException("work list | work prio <roomKey> <0-30> | work target <roomKey> <n|max>");
		int n = 0;
		for (RoomInstance ins : Lists.of(emp.blueprint().all())) {
			ins.employees().neededSet(a[3].equals("max") ? ins.employees().max() : Commands.i(a, 3));
			n++;
		}
		return Commands.m("key", a[2], "instances", n);
	}

	private ModEconomy() {}
}
