package lordapcom.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import game.faction.FACTIONS;
import game.tourism.TOURISM;
import init.constant.C;
import init.race.RACES;
import init.race.Race;
import init.type.CAUSE_ARRIVE;
import init.type.CAUSE_ARRIVES;
import init.type.CAUSE_LEAVE;
import init.type.CAUSE_LEAVES;
import init.type.CRIMES;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.HGROUP;
import init.type.HTYPE;
import init.type.HTYPES;
import init.type.WGROUP;
import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.entry.Immigration;
import settlement.main.SETT;
import settlement.path.path.SPath;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.main.employment.RoomEmployment;
import settlement.room.main.employment.RoomEmploymentSimple;
import settlement.stats.STATS;
import settlement.stats.service.StatService;
import settlement.stats.standing.STANDINGS;
import settlement.stats.standing.StandingCitizen;
import snake2d.util.datatypes.COORDINATE;

/** Population and AI: spawn/kill/immigrate/mood/employ/path/teleport. */
final class ModPeople {

	static void register() {
		Registry.add("people", "people - races, humanoid types (htype keys), classes, leave causes", a -> people());
		Registry.add("groups", "groups - HGROUP (race x class, Species Customs Gate filter) and WGROUP (race x worker type, Workplace Species Filter) index -> key",
				a -> groups());
		Registry.add("spawn", "spawn <race> <type|NOBLE> <n> [x y] - create humanoids like immigration (no x y = random entry point)", ModPeople::spawn);
		Registry.add("kill", "kill <id|all|race=r|type=T|class=c ...> [cause=KEY] - Humanoid.kill (default cause AGE; causes in 'people')", ModPeople::kill);
		Registry.add("immigrate", "immigrate | immigrate <race> <n> [type] | immigrate auto <race> <n> - read state / admit n (UI admit button, or debug spawn for other types) / set auto-admit cap", ModPeople::immigrate);
		Registry.add("mood", "mood | mood set <cit|slave> <loyalty|happiness> <race|all> <value|+d|-d> | mood buff <cit|slave> [days] - read / set / emergency buff", ModPeople::mood);
		Registry.add("employ", "employ <id> <roomIndex|none> | employ list <roomIndex> - assign/unassign a worker, list a room's workers", ModPeople::employ);
		Registry.add("workprio", "workprio <blueprintKey> [<wgroup index|key|all> <0-5>] - read / set worker-group priority of a workplace (priority panel)", ModPeople::workprio);
		Registry.add("path", "path <id> - plan, destination and full path tiles of a humanoid", ModPeople::path);
		Registry.add("reach", "reach <id> <x> <y> - can this humanoid path to the tile (same SPath.request the AI uses)", ModPeople::reach);
		Registry.add("tp", "tp <id> <x> <y> - teleport a humanoid and interrupt its AI", ModPeople::tp);
	}

	// ---- lookups ------------------------------------------------------------------------------

	private static Race race(String k) {
		for (Race r : Lists.of(RACES.all()))
			if (r.key.equalsIgnoreCase(k))
				return r;
		throw new IllegalArgumentException("unknown race '" + k + "' (see 'people')");
	}

	private static HTYPE type(String k) {
		for (HTYPE t : Lists.of(HTYPES.ALL()))
			if (t.key.equalsIgnoreCase(k))
				return t;
		throw new IllegalArgumentException("unknown type '" + k + "' (see 'people')");
	}

	private static HCLASS cls(String k) {
		if (k.equalsIgnoreCase("cit"))
			return HCLASSES.CITIZEN();
		if (k.equalsIgnoreCase("slave"))
			return HCLASSES.SLAVE();
		for (HCLASS c : Lists.of(HCLASSES.ALL()))
			if (c.key.equalsIgnoreCase(k))
				return c;
		throw new IllegalArgumentException("unknown class '" + k + "' (see 'people')");
	}

	private static CAUSE_LEAVE cause(String k) {
		for (CAUSE_LEAVE c : CAUSE_LEAVES.ALL())
			if (c.key.equalsIgnoreCase(k))
				return c;
		throw new IllegalArgumentException("unknown cause '" + k + "' (see 'people')");
	}

	static Humanoid human(String id) {
		ENTITY e = SETT.ENTITIES().getByID(Integer.parseInt(id));
		if (!(e instanceof Humanoid) || e.isRemoved())
			throw new IllegalArgumentException("no live humanoid with id " + id);
		return (Humanoid) e;
	}

	private static int arg(String[] a, int i, String usage) {
		if (i >= a.length)
			throw new IllegalArgumentException(usage);
		return Integer.parseInt(a[i]);
	}

	// ---- people / groups ----------------------------------------------------------------------

	private static Object people() {
		List<Object> races = new ArrayList<>(), types = new ArrayList<>(), classes = new ArrayList<>(), causes = new ArrayList<>();
		for (Race r : Lists.of(RACES.all()))
			races.add(Commands.m("key", r.key, "index", r.index(), "name", r.info.name, "playable", RACES.playable().contains(r)));
		for (HTYPE t : Lists.of(HTYPES.ALL()))
			types.add(Commands.m("key", t.key, "class", t.CLASS.key, "works", t.isWorks(), "hostile", t.isHostile()));
		for (HCLASS c : Lists.of(HCLASSES.ALL()))
			classes.add(Commands.m("key", c.key, "player", c.player));
		for (CAUSE_LEAVE c : CAUSE_LEAVES.ALL())
			causes.add(c.key);
		return Commands.m("races", races, "types", types, "classes", classes, "leaveCauses", causes, "playerRace", FACTIONS.player().race().key);
	}

	private static Object groups() {
		List<Object> h = new ArrayList<>(), w = new ArrayList<>();
		for (HGROUP g : Lists.of(HGROUP.all()))
			h.add(Commands.m("index", g.index, "key", g.key, "race", g.race.key, "class", g.type.key));
		for (WGROUP g : Lists.of(WGROUP.all()))
			w.add(Commands.m("index", g.index, "key", g.key, "race", g.race.key, "type", g.type.key));
		return Commands.m("hgroup", h, "wgroup", w);
	}

	// ---- spawn --------------------------------------------------------------------------------

	// game path: settlement/entry/PeopleSpawner.java:130-153 spawn(); Humanoids.create (settlement/entity/humanoid/Humanoids.java:262)
	private static Object spawn(String[] a) {
		String usage = "spawn <race> <type|NOBLE> <n> [x y]";
		if (a.length < 4)
			throw new IllegalArgumentException(usage);
		Race r = race(a[1]);
		boolean noble = a[2].equalsIgnoreCase("NOBLE");
		HTYPE t = noble ? HTYPES.SUBJECT() : type(a[2]);
		int n = Integer.parseInt(a[3]);
		boolean at = a.length >= 6;
		int x = at ? Commands.i(a, 4) : 0, y = at ? Commands.i(a, 5) : 0;
		List<Object> ids = new ArrayList<>();
		for (int k = 0; k < n; k++) {
			if (!at) {
				COORDINATE c = SETT.ENTRY().points.randomReachable(); // PeopleSpawner.update:117
				if (c == null)
					throw new IllegalArgumentException("no reachable entry point; pass x y");
				x = c.x();
				y = c.y();
			}
			if (!SETT.IN_BOUNDS(x, y))
				throw new IllegalArgumentException("tile out of bounds: " + x + " " + y);
			// ponytail: no connectivity check at an explicit tile, the caller picks a walkable one
			CAUSE_ARRIVE cause = t == HTYPES.CHILD() || t == HTYPES.CHILD_SLAVE() ? CAUSE_ARRIVES.BORN() : CAUSE_ARRIVES.IMMIGRATED();
			Humanoid h = SETT.HUMANOIDS().create(r, x, y, t, t == HTYPES.TOURIST() ? null : cause);
			if (h == null || h.isRemoved())
				continue;
			if (h.indu().clas().player)
				STATS.Arrive(h); // PeopleSpawner.init:158
			if (t == HTYPES.PRISONER())
				STATS.LAW().prisonerType.set(h.indu(), CRIMES.WAR()); // PeopleSpawner:145
			if (t == HTYPES.TOURIST()) { // game/tourism/Updater.java:258-263 pspawn()
				STATS.WORK().profession.set(h.indu(), r.tourism().getAttraction(0));
				StatService s = TOURISM.service(h.indu());
				if (s != null)
					s.clearAccess(h.indu());
			}
			if (noble)
				h.nobleSet(); // game/nobility/NOBLES.java:317
			ids.add(h.id());
		}
		return Commands.m("created", ids.size(), "ids", ids);
	}

	// ---- kill ---------------------------------------------------------------------------------

	// game path: settlement/entity/humanoid/Humanoids.java:81-99 "kill" debug tool -> Humanoid.kill(false, cause) (Humanoid.java:411)
	private static Object kill(String[] a) {
		if (a.length < 2)
			throw new IllegalArgumentException("kill <id|all|race=r|type=T|class=c ...> [cause=KEY]");
		CAUSE_LEAVE cause = CAUSE_LEAVES.AGE();
		Race r = null;
		HTYPE t = null;
		HCLASS c = null;
		int id = -1;
		for (int i = 1; i < a.length; i++) {
			String s = a[i];
			if (s.equalsIgnoreCase("all"))
				continue;
			int eq = s.indexOf('=');
			if (eq < 0)
				id = Integer.parseInt(s);
			else {
				String k = s.substring(0, eq).toLowerCase(), v = s.substring(eq + 1);
				if (k.equals("race"))
					r = race(v);
				else if (k.equals("type"))
					t = type(v);
				else if (k.equals("class"))
					c = cls(v);
				else if (k.equals("cause"))
					cause = cause(v);
				else
					throw new IllegalArgumentException("unknown filter " + k);
			}
		}
		List<Humanoid> hit = new ArrayList<>();
		for (ENTITY e : SETT.ENTITIES().getAllEnts()) {
			if (!(e instanceof Humanoid) || e.isRemoved())
				continue;
			Humanoid h = (Humanoid) e;
			if ((id < 0 || h.id() == id) && (r == null || h.race() == r) && (t == null || h.indu().hType() == t)
					&& (c == null || h.indu().clas() == c))
				hit.add(h);
		}
		for (Humanoid h : hit)
			h.kill(false, cause);
		return Commands.m("killed", hit.size());
	}

	// ---- immigration --------------------------------------------------------------------------

	// game path: settlement/entry/Immigration.java:181 admit (immigration UI admit), :177 auto (auto-admit slider), settlement/entry/PeopleSpawner.java:40 debug "Spawn immigrants" -> SENTRY.add
	private static Object immigrate(String[] a) {
		Immigration im = SETT.ENTRY().immi();
		if (a.length == 1) {
			Map<String, Object> out = Commands.m();
			for (Race r : Lists.of(RACES.all()))
				out.put(r.key, Commands.m("wanted", im.wanted(r), "admitted", im.admitted(r), "auto", im.auto(r).get(),
						"maxPop", im.maxPop(r), "secondsTillNext", im.secondsTillNext(r), "perDay", im.immigrantsPerDay(r)));
			return out;
		}
		if (a[1].equalsIgnoreCase("auto")) {
			Race r = race(a.length > 2 ? a[2] : "");
			im.auto(r).set(arg(a, 3, "immigrate auto <race> <n>"));
			return Commands.m("auto", im.auto(r).get());
		}
		Race r = race(a[1]);
		int n = arg(a, 2, "immigrate <race> <n> [type]");
		HTYPE t = a.length > 3 ? type(a[3]) : HTYPES.SUBJECT();
		if (t == HTYPES.SUBJECT())
			im.admit(r, n);
		else
			SETT.ENTRY().add(r, t, n);
		return Commands.m("onTheirWay", SETT.ENTRY().onTheirWay(r, t));
	}

	// ---- mood ---------------------------------------------------------------------------------

	// game path: settlement/stats/standing/StandingCitizen.java:120 debug "happiness++" -> CitizenThing.set(race, v) (package-private, reflection);
	// loyalty follows StandingCitizen.Main.update (:276) which reads the private mains[] array; buff = STANDINGS.emergency (STANDINGS.java:140)
	private static Object mood(String[] a) {
		if (a.length == 1) {
			Map<String, Object> out = Commands.m();
			for (HCLASS cl : new HCLASS[] { HCLASSES.CITIZEN(), HCLASSES.SLAVE() }) {
				StandingCitizen s = STANDINGS.get(cl);
				Map<String, Object> byRace = Commands.m();
				for (Race r : Lists.of(RACES.all()))
					byRace.put(r.key, Commands.m("happiness", s.happiness.getD(r), "loyalty", s.loyalty.getD(r), "loyaltyTarget",
							s.loyaltyTarget.getD(r), "fulfillment", s.fullfillment.getD(r), "expectation", s.expectation.getD(r)));
				out.put(cl.key, Commands.m("total", Commands.m("loyalty", s.current(), "target", s.target()), "byRace", byRace));
			}
			return out;
		}
		String usage = "mood set <cit|slave> <loyalty|happiness> <race|all> <value|+d|-d> | mood buff <cit|slave> [days]";
		if (a[1].equalsIgnoreCase("buff")) {
			HCLASS cl = cls(a.length > 2 ? a[2] : "cit");
			double days = a.length > 3 ? Double.parseDouble(a[3]) : 8;
			STANDINGS.emergency(cl, days * game.time.TIME.secondsPerDay());
			return Commands.m("buffDays", days);
		}
		if (!a[1].equalsIgnoreCase("set") || a.length < 6)
			throw new IllegalArgumentException(usage);
		StandingCitizen s = STANDINGS.get(cls(a[2]));
		boolean loy = a[3].equalsIgnoreCase("loyalty");
		if (!loy && !a[3].equalsIgnoreCase("happiness"))
			throw new IllegalArgumentException(usage);
		boolean rel = a[5].startsWith("+") || a[5].startsWith("-");
		double v = Double.parseDouble(a[5]);
		Map<String, Object> out = Commands.m();
		try {
			Method set = StandingCitizen.CitizenThing.class.getDeclaredMethod("set", Race.class, double.class);
			set.setAccessible(true);
			Field mains = StandingCitizen.class.getDeclaredField("mains");
			mains.setAccessible(true);
			// resolve before the loop: the game's LISTs share one iterator, so a nested walk of RACES.all() never ends
			Race only = a[4].equalsIgnoreCase("all") ? null : race(a[4]);
			for (Race r : Lists.of(RACES.all())) {
				if (only != null && r != only)
					continue;
				StandingCitizen.CitizenThing th = loy ? s.loyalty : s.happiness;
				double now = th.getD(r), nv = Math.max(0, rel ? now + v : v);
				set.invoke(th, r, nv);
				if (loy)
					((double[]) mains.get(s))[r.index()] = nv;
				out.put(r.key, nv);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException("mood reflection failed: " + e);
		}
		return Commands.m("set", out, "note", "the game recomputes happiness each tick and loyalty drifts to its target; use mood buff for a lasting loyalty lift");
	}

	// ---- employment ---------------------------------------------------------------------------

	private static RoomInstance room(String idx) {
		Room r = SETT.ROOMS().map.getByIndex(Integer.parseInt(idx));
		if (!(r instanceof RoomInstance))
			throw new IllegalArgumentException("no room instance with index " + idx + " (see 'rooms')");
		return (RoomInstance) r;
	}

	// game path: settlement/stats/colls/StatsWork.java:397 EMPLOYED.set(h, room), what RoomEmploymentSimple.EmployerSimple.employ (RoomEmploymentSimple.java:251) calls
	private static Object employ(String[] a) {
		String usage = "employ <id> <roomIndex|none> | employ list <roomIndex>";
		if (a.length < 3)
			throw new IllegalArgumentException(usage);
		if (a[1].equalsIgnoreCase("list")) {
			RoomInstance ins = room(a[2]);
			List<Object> ids = new ArrayList<>();
			for (Humanoid h : ins.employees().employees())
				ids.add(h.id());
			return Commands.m("employed", ins.employees().employed(), "target", ins.employees().target(), "ids", ids);
		}
		Humanoid h = human(a[1]);
		if (a[2].equalsIgnoreCase("none")) {
			STATS.WORK().EMPLOYED.set(h, null);
			return Commands.m("employed", false);
		}
		if (!h.indu().hType().isWorks())
			throw new IllegalArgumentException(h.indu().hType().key + " cannot work (works: CITIZEN, SLAVE)");
		RoomInstance ins = room(a[2]);
		if (ins.blueprint() instanceof RoomBlueprintIns && ((RoomBlueprintIns<?>) ins.blueprint()).employment() == null || !ins.active() || !ins.employees().active())
			throw new IllegalArgumentException("room " + a[2] + " has no active workplace");
		STATS.WORK().EMPLOYED.set(h, ins);
		return Commands.m("employed", true, "room", ins.index(), "note", "the game's employer rebalances workers on its own and may move this one later");
	}

	// game path: settlement/room/main/employment/RoomEmployment.java:43 priorities.set(WGROUP, 0-5) (workplace priority panel; triggers employer.updateAll)
	private static Object workprio(String[] a) {
		if (a.length < 2)
			throw new IllegalArgumentException("workprio <blueprintKey> [<wgroup index|key|all> <0-5>]");
		RoomBlueprintImp b = Build.blueprint(a[1]);
		RoomEmploymentSimple es = b instanceof RoomBlueprintIns ? ((RoomBlueprintIns<?>) b).employment() : null;
		if (!(es instanceof RoomEmployment))
			throw new IllegalArgumentException(a[1] + " has no worker priorities");
		RoomEmployment e = (RoomEmployment) es;
		if (a.length >= 4) {
			int p = Integer.parseInt(a[3]);
			for (WGROUP g : Lists.of(WGROUP.all()))
				if (a[2].equalsIgnoreCase("all") || a[2].equalsIgnoreCase(g.key) || a[2].equals(String.valueOf(g.index)))
					e.priorities.set(g, p);
		}
		Map<String, Object> out = Commands.m();
		for (WGROUP g : Lists.of(WGROUP.all()))
			out.put(g.index + " " + g.key, e.priorities.get(g));
		return out;
	}

	// ---- path / reach / tp --------------------------------------------------------------------

	private static Integer[] xy(int x, int y) {
		return new Integer[] { x, y };
	}

	private static List<Object> tiles(SPath p) {
		List<Object> out = new ArrayList<>();
		int cur = p.getCurrentI();
		for (int i = 0; i < p.length(); i++) { // setCurrentI, as SPath.render does (SPath.java:68), avoids setNext's threaded resume
			p.setCurrentI(i);
			out.add(xy(p.x(), p.y() ));
		}
		p.setCurrentI(cur);
		return out;
	}

	// game path: AIManager.path (settlement/entity/humanoid/ai/main/AIManager.java:81,670) + getDestination (:663)
	private static Object path(String[] a) {
		Humanoid h = human(a.length > 1 ? a[1] : "");
		if (!(h.ai() instanceof AIManager))
			throw new IllegalArgumentException("humanoid has no AIManager");
		AIManager ai = (AIManager) h.ai();
		SPath p = ai.path();
		COORDINATE d = ai.getDestination();
		return Commands.m("at", xy(h.tc().x(), h.tc().y() ), "plan", Commands.plan(h), "sub", ai.plansub() == null ? null : ai.plansub().key,
				"walking", d != null, "successful", p.isSuccessful(), "dest", p.isSuccessful() ? xy(p.destX(), p.destY() ) : null,
				"planTile", xy(ai.planTile.x(), ai.planTile.y() ), "length", p.length(), "currentIndex", p.getCurrentI(),
				"tiles", p.isSuccessful() ? tiles(p) : new ArrayList<>());
	}

	// game path: settlement/path/path/SPath.java:131 request(Humanoid, dx, dy), the call AI walk plans make
	private static Object reach(String[] a) {
		Humanoid h = human(a.length > 1 ? a[1] : "");
		SPath p = new SPath();
		boolean ok = p.request(h, Commands.i(a, 2), Commands.i(a, 3));
		return Commands.m("reachable", ok, "length", ok ? p.length() : 0, "tiles", ok ? tiles(p) : new ArrayList<>());
	}

	// game path: same moves the game uses to relocate a humanoid: body().moveC (AIModule_Home.java:354) + ENTITIES().move (Humanoid.teleportAndInitInDiv:568-569)
	private static Object tp(String[] a) {
		Humanoid h = human(a.length > 1 ? a[1] : "");
		int x = Commands.i(a, 2), y = Commands.i(a, 3);
		if (!SETT.IN_BOUNDS(x, y))
			throw new IllegalArgumentException("tile out of bounds");
		h.physics.body().moveC(x * C.TILE_SIZE + C.TILE_SIZEH, y * C.TILE_SIZE + C.TILE_SIZEH);
		SETT.ENTITIES().move(h);
		h.interrupt(); // Humanoid.java:649
		return Commands.m("at", xy(h.tc().x(), h.tc().y() ));
	}

	private ModPeople() {}
}
