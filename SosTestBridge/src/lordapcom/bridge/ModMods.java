package lordapcom.bridge;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import settlement.main.SETT;
import settlement.room.main.RoomInstance;
import vizardalpha.polyphon.api.Shadow;

/**
 * Test hooks for our own mods, by reflection (the mods stay optional): the same calls their panels make when the
 * player presses "Apply" (Species Customs Gate GatePanel -> Session.edit; Workplace Species Filter tool -> Filters.set).
 */
final class ModMods {

	static void register() {
		Registry.add("gate", "gate list | gate set <x> <y> <bits|all|none> [inv] - Species Customs Gate filters (same as the gate panel's Apply)", ModMods::gate);
		Registry.add("wsf", "wsf tool | wsf probe <x> <y> | wsf <x> <y> | wsf set <x> <y> <work|use> <bits|all> - Workplace Species Filter per building (same as the filter tool)", ModMods::wsf);
		Registry.add("cdstate", "cdstate - Cartographer: Capital Closed inputs, island/docks, boats, invaders (count, in water, avg distance to throne, doing)", ModMods::cdstate);
		Registry.add("cdregroup", "cdregroup <humanId> - Cartographer: run the landing regroup for this soldier's division at its tile and report the formation before/after", ModMods::cdregroup);
		Registry.add("fts", "fts road [on|off] | fts fish <x> <y> | fts pillars <blueprint> <x> <y> <w> <h> - Fixing the Syx: the code behind T and Ctrl+click (keys/mouse skipped); the room plan is dropped afterwards", ModMods::fts);
	}

	/** One-call snapshot of a sea capital: the "Capital Closed" inputs, the island/dock state, boats and invaders. */
	private static Object cdstate(String[] a) throws Exception {
		Class<?> isl = Class.forName("lordapcom.cdream.Island"), bo = Class.forName("lordapcom.cdream.Boats");
		java.lang.reflect.Field fi = isl.getDeclaredField("island"), fl = isl.getDeclaredField("locked"), fd = isl.getDeclaredField("docks"), ft = bo.getDeclaredField("trips");
		fi.setAccessible(true); fl.setAccessible(true); fd.setAccessible(true); ft.setAccessible(true);
		Object updater = vizardalpha.polyphon.api.Shadow.get(SETT.ENTRY(), "updater");
		snake2d.util.datatypes.COORDINATE th = settlement.room.main.throne.THRONE.coo();
		int[] n = new int[4]; // enemies, in water, sum of distance to throne, citizens+slaves
		Map<String, Integer> doing = new java.util.TreeMap<>();
		for (settlement.entity.ENTITY e : SETT.ENTITIES().getAllEnts()) {
			if (!(e instanceof settlement.entity.humanoid.Humanoid h) || h.isRemoved())
				continue;
			if (h.indu().clas().player) {
				n[3]++;
				continue;
			}
			if (!h.indu().hType().isHostile())
				continue;
			n[0]++;
			int x = h.tc().x(), y = h.tc().y();
			if (SETT.TERRAIN().WATER.is.is(x, y))
				n[1]++;
			n[2] += Math.abs(x - th.x()) + Math.abs(y - th.y());
			if (h.ai() instanceof settlement.entity.humanoid.ai.main.AIManager ai) {
				snake2d.util.sprite.text.Str s = new snake2d.util.sprite.text.Str(80);
				ai.getOccupation(h, s);
				doing.merge(s.toString(), 1, Integer::sum);
			}
		}
		String inv = null;
		snake2d.util.sets.LIST<?> act = vizardalpha.polyphon.api.Shadow.get(SETT.INVADOR(), "active"); // as the 'invasion' command
		for (Object i : Lists.of(act))
			inv = String.valueOf((Object) vizardalpha.polyphon.api.Shadow.get(i, "state"));
		// goods lying on the ground (deliveries are dropped there when no warehouse takes them): amount and piles per resource
		Map<String, int[]> ground = new java.util.TreeMap<>();
		for (int y = 0; y < SETT.THEIGHT; y++)
			for (int x = 0; x < SETT.TWIDTH; x++) {
				settlement.thing.ThingsResources.ScatteredResource r = SETT.THINGS().resources.get(x, y);
				if (r == null)
					continue;
				int[] g = ground.computeIfAbsent(r.resource().key, k -> new int[2]);
				g[0] += r.amount();
				g[1]++;
			}
		Map<String, String> groundOut = new java.util.TreeMap<>();
		ground.forEach((k, g) -> groundOut.put(k, g[0] + " em " + g[1] + " pilhas"));
		List<Object> divs = new ArrayList<>();
		for (game.battle.div.Div d : Lists.of(game.GAME.ARMIES().enemy().divisions()))
			if (d.menNrOf() > 0) {
				Map<String, Object> f = formation(d);
				f.put("men", d.menNrOf());
				divs.add(f);
			}
		return Commands.m("closedMsg", (Boolean) vizardalpha.polyphon.api.Shadow.invoke(updater, "isClosed"), "invading", SETT.INVADOR().invading(),
				"closedNow", SETT.ENTRY().isClosed(), "reachableEntries", SETT.ENTRY().points.reachable().size(), "island", fi.getBoolean(null),
				"locked", fl.getBoolean(null), "docks", ((int[]) fd.get(null)).length, "boats", ((java.util.Collection<?>) ft.get(null)).size(),
				"invasion", inv, "enemies", n[0], "enemiesInWater", n[1], "enemyAvgDist", n[0] == 0 ? null : n[2] / n[0], "doing", doing, "enemyDivs", divs, "ground", groundOut,
				"people", n[3], "pop", settlement.stats.POP.pop(), "day", game.time.TIME.days().bitsSinceStart());
	}

	private static Object cdregroup(String[] a) throws Exception {
		settlement.entity.humanoid.Humanoid h = ModPeople.human(a[1]);
		game.battle.div.Div div = h.division();
		if (div == null)
			throw new IllegalArgumentException("no division");
		Map<String, Object> before = formation(div);
		java.lang.reflect.Field rg = Class.forName("lordapcom.cdream.Boats").getDeclaredField("regrouped");
		rg.setAccessible(true);
		((Map<?, ?>) rg.get(null)).remove(div);
		Method m = Class.forName("lordapcom.cdream.Boats").getDeclaredMethod("regroup", settlement.entity.humanoid.Humanoid.class, int.class, int.class);
		m.setAccessible(true);
		m.invoke(null, h, h.tc().x(), h.tc().y());
		return Commands.m("at", h.tc().x() + "," + h.tc().y(), "throne", settlement.room.main.throne.THRONE.coo().x() + "," + settlement.room.main.throne.THRONE.coo().y(),
				"men", div.menNrOf(), "before", before, "after", formation(div));
	}

	private static Map<String, Object> formation(game.battle.div.Div d) {
		game.battle.formation.DivFormationImp f = new game.battle.formation.DivFormationImp();
		d.order().dest.get(f);
		snake2d.util.datatypes.COORDINATE c = f.deployed() > 0 ? f.centreTile() : null;
		game.battle.thread.order.BattleOrderTask t = new game.battle.thread.order.BattleOrderTask();
		d.order().task.get(t);
		return Commands.m("deployed", f.deployed(), "centre", c == null ? null : c.x() + "," + c.y(), "task", t.task().name(),
				"divCentre", (d.centre().cX() >> init.constant.C.T_SCROLL) + "," + (d.centre().cY() >> init.constant.C.T_SCROLL));
	}

	// Fixing the Syx: the Ctrl+click handlers (AreaTools.onClick) minus the key/mouse read, on the real room placer.
	private static Object fts(String[] a) throws Exception {
		String sub = a.length > 1 ? a[1] : "";
		if (sub.equals("road")) {
			java.lang.reflect.Field on = Class.forName("lordapcom.fts.RoadMountain").getDeclaredField("on");
			on.setAccessible(true);
			if (a.length > 2)
				on.setBoolean(null, a[2].equals("on")); // what the T key toggles
			return Commands.m("on", on.getBoolean(null));
		}
		settlement.room.main.placement.RoomPlacer pl = SETT.ROOMS().placement.placer;
		try {
			if (sub.equals("fish")) {
				int x = Commands.i(a, 2), y = Commands.i(a, 3);
				pl.init(Build.blueprint("FISHERY_NORMAL"), 0);
				Method fill = Class.forName("lordapcom.fts.AreaTools").getDeclaredMethod("fillWater", view.tool.PlacableMulti.class,
						settlement.room.main.placement.RoomPlacer.class, int.class, int.class);
				fill.setAccessible(true);
				fill.invoke(null, pl.area(), pl, x, y);
				return plan(pl, x, y);
			}
			if (sub.equals("housescan")) { // dry run of the single-item placer (PlacerItemSingle) over an area: nothing is placed
				settlement.room.main.RoomBlueprintImp b = Build.blueprint(a[2]);
				int g = Commands.i(a, 3), size = Commands.i(a, 4), x1 = Commands.i(a, 5), y1 = Commands.i(a, 6), x2 = Commands.i(a, 7), y2 = Commands.i(a, 8);
				pl.init(b, 0);
				view.tool.PlacableFixed pp = pl.item(g);
				Map<String, Integer> why = new java.util.TreeMap<>();
				List<String> ok = new ArrayList<>(), blockedWill = new ArrayList<>();
				String bw = view.tool.PlacableMessages.¤¤BLOCKED_WILL.toString();
				for (int r = 0; r < 4; r++) {
					pp.rotSet(r);
					pp.sizeSet(size);
					for (int y = y1; y <= y2; y++)
						for (int x = x1; x <= x2; x++) {
							pp.init(x + pp.width() / 2, y + pp.height() / 2);
							CharSequence e = pp.placableWhole(x, y);
							for (int dy = 0; e == null && dy < pp.height(); dy++)
								for (int dx = 0; e == null && dx < pp.width(); dx++)
									e = pp.placable(x + dx, y + dy, dx, dy);
							String k = e == null ? "ok" : e.length() == 0 ? "(item blocked)" : e.toString();
							why.merge(k, 1, Integer::sum);
							if (e == null)
								ok.add(x + "," + y + ",r" + r);
							else if (k.equals(bw))
								blockedWill.add(x + "," + y + ",r" + r);
						}
				}
				return Commands.m("results", why, "ok", ok, "blockedWill", blockedWill);
			}
			if (sub.equals("pillars")) {
				settlement.room.main.RoomBlueprintImp b = Build.blueprint(a[2]);
				int x = Commands.i(a, 3), y = Commands.i(a, 4), w = Commands.i(a, 5), h = Commands.i(a, 6);
				pl.init(b, 0);
				Build.runTool((view.tool.PlacableMulti) pl.area(), x, y, w, h);
				Map<String, Object> before = plan(pl, x, y);
				int sx = -1, sy = -1; // first yellow tile, what the player would Ctrl+click
				for (int yy = y; yy < y + h && sx < 0; yy++)
					for (int xx = x; xx < x + w && sx < 0; xx++)
						if (SETT.ROOMS().placement.embryo.is(xx, yy) && pl.cost().is(xx, yy)) {
							sx = xx;
							sy = yy;
						}
				if (sx < 0)
					return Commands.m("before", before, "note", "no yellow tile, nothing to do");
				Class<?> p = Class.forName("lordapcom.fts.Pillars");
				Method start = p.getDeclaredMethod("start", settlement.room.main.placement.RoomPlacer.class, settlement.room.main.RoomBlueprintImp.class, int.class, int.class);
				start.setAccessible(true);
				boolean started = (Boolean) start.invoke(null, pl, b, sx, sy);
				Method tick = p.getDeclaredMethod("tick");
				tick.setAccessible(true);
				java.lang.reflect.Field last = p.getDeclaredField("last");
				last.setAccessible(true);
				java.lang.reflect.Field queue = p.getDeclaredField("queue");
				queue.setAccessible(true);
				int steps = 0;
				while (!((java.util.Collection<?>) queue.get(null)).isEmpty() && steps++ < 500) {
					last.setLong(null, 0L); // skip the 0.5 s wait between clusters (it only paces the yellow-map refresh)
					tick.invoke(null);
				}
				return Commands.m("clicked", sx + "," + sy, "started", started, "before", before, "after", plan(pl, x, y));
			}
		} finally {
			pl.init(null, 0);
		}
		throw new IllegalArgumentException("fts road [on|off] | fts fish <x> <y> | fts pillars <blueprint> <x> <y> <w> <h>");
	}

	/** The room plan as the placer holds it: painted tiles, how many are yellow (need support), bounding box, text map. */
	private static Map<String, Object> plan(settlement.room.main.placement.RoomPlacer pl, int cx, int cy) {
		vizardalpha.polyphon.api.Shadow.set(pl.cost(), "tick", Integer.valueOf(-1));
		pl.cost().support();
		int R = settlement.room.main.Room.MAX_DIM + 2;
		int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, n = 0, yellow = 0, notShallow = 0;
		for (int y = cy - R; y <= cy + R; y++)
			for (int x = cx - R; x <= cx + R; x++)
				if (SETT.IN_BOUNDS(x, y) && SETT.ROOMS().placement.embryo.is(x, y)) {
					n++;
					if (pl.cost().is(x, y))
						yellow++;
					if (!SETT.TERRAIN().WATER.SHALLOW.is(x, y))
						notShallow++;
					x1 = Math.min(x1, x);
					y1 = Math.min(y1, y);
					x2 = Math.max(x2, x);
					y2 = Math.max(y2, y);
				}
		List<String> rows = new ArrayList<>();
		if (n > 0)
			for (int y = y1 - 1; y <= y2 + 1; y++) {
				StringBuilder s = new StringBuilder();
				for (int x = x1 - 1; x <= x2 + 1; x++)
					s.append(!SETT.IN_BOUNDS(x, y) ? ' ' : SETT.ROOMS().placement.embryo.is(x, y) ? (pl.cost().is(x, y) ? 'Y' : '+')
							: SETT.TERRAIN().WATER.SHALLOW.is(x, y) ? '-' : SETT.TERRAIN().WATER.DEEP.is(x, y) ? '~' : '.');
				rows.add(s.toString());
			}
		return Commands.m("tiles", n, "yellow", yellow, "notShallow", notShallow, "placerSize", pl.size(), "box",
				n == 0 ? null : (x1 + "," + y1 + "-" + x2 + "," + y2 + " (" + (x2 - x1 + 1) + "x" + (y2 - y1 + 1) + ")"),
				"maxDim", settlement.room.main.Room.MAX_DIM, "maxSize", settlement.room.main.Room.MAX_SIZE, "map", rows);
	}

	private static int[] bits(String s) {
		if (s.equalsIgnoreCase("all"))
			return null; // no filter: everyone allowed
		if (s.equalsIgnoreCase("none"))
			return new int[] { 0 };
		return new int[] { Integer.parseInt(s) };
	}

	private static Object gate(String[] a) throws Exception {
		Class<?> ses = Class.forName("lordapcom.gate.Session");
		Method get = ses.getDeclaredMethod("get");
		get.setAccessible(true);
		Object s = get.invoke(null);
		String sub = a.length > 1 ? a[1] : "list";
		if (sub.equals("set")) {
			if (a.length < 5)
				throw new IllegalArgumentException("gate set <x> <y> <bits|all|none> [inv]");
			Object g = Shadow.invoke(s, "gateAt", Commands.i(a, 2), Commands.i(a, 3));
			if (g == null)
				throw new IllegalArgumentException("no gate at " + a[2] + "," + a[3] + " (gate list)");
			boolean inv = a.length > 5 && a[5].equals("inv");
			Method edit = null;
			for (Method m : ses.getDeclaredMethods())
				if (m.getName().equals("edit"))
					edit = m;
			edit.setAccessible(true);
			edit.invoke(s, g, bits(a[4]), inv); // GatePanel.java:87 s.edit(g, normalize(sel), inv)
			return describe(g, ses);
		}
		Object[] gates = Shadow.get(s, "gates");
		List<Object> l = new ArrayList<>();
		if (gates != null)
			for (Object g : gates)
				l.add(describe(g, ses));
		return l;
	}

	private static Map<String, Object> describe(Object g, Class<?> ses) throws Exception {
		Method arrow = ses.getDeclaredMethod("arrowName", g.getClass());
		arrow.setAccessible(true);
		int[] b = Shadow.get(g, "bits");
		return Commands.m("x1", Shadow.get(g, "x1"), "y1", Shadow.get(g, "y1"), "x2", Shadow.get(g, "x2"), "y2", Shadow.get(g, "y2"),
			"arrow", arrow.invoke(null, g), "bits", b == null ? "all" : b[0]);
	}

	private static Object wsf(String[] a) throws Exception {
		Class<?> f = Class.forName("lordapcom.wsf.Filters");
		if (a.length > 1 && a[1].equals("probe")) // the game's own service search, run as one humanoid of each race/class
			return probe(f, Commands.i(a, 2), Commands.i(a, 3));
		if (a.length > 1 && a[1].equals("tool")) { // opens the filter tool, as Tools -> Species filter
			java.lang.reflect.Constructor<?> c = Class.forName("lordapcom.wsf.FilterTool").getDeclaredConstructor();
			c.setAccessible(true);
			view.main.VIEW.s().tools.place((view.tool.PlacableMulti) c.newInstance());
			return "open";
		}
		boolean set = a.length > 1 && a[1].equals("set");
		int o = set ? 2 : 1;
		RoomInstance ins = SETT.ROOMS().map.instance.get(Commands.i(a, o), Commands.i(a, o + 1));
		if (ins == null)
			throw new IllegalArgumentException("no room at " + a[o] + "," + a[o + 1]);
		if (set) {
			if (a.length < 6)
				throw new IllegalArgumentException("wsf set <x> <y> <work|use> <bits|all>");
			Method m = f.getDeclaredMethod("set", RoomInstance.class, boolean.class, int[].class);
			m.setAccessible(true);
			m.invoke(null, ins, a[4].equals("work"), bits(a[5]));
		}
		Method get = f.getDeclaredMethod("get", RoomInstance.class);
		get.setAccessible(true);
		Object e = get.invoke(null, ins);
		int[] w = e == null ? null : Shadow.get(e, "w");
		int[] u = e == null ? null : Shadow.get(e, "u");
		return Commands.m("room", ins.blueprint().key, "work", w == null ? "all" : w[0], "use", u == null ? "all" : u[0]);
	}

	private static Object probe(Class<?> filters, int x, int y) throws Exception {
		RoomInstance ins = SETT.ROOMS().map.instance.get(x, y);
		if (ins == null || !(ins.blueprint() instanceof settlement.room.service.module.RoomServiceAccess.ROOM_SERVICE_ACCESS_HASER sh))
			throw new IllegalArgumentException("no service room at " + x + "," + y);
		java.lang.reflect.Field rq = filters.getDeclaredField("REQ");
		rq.setAccessible(true);
		@SuppressWarnings("unchecked")
		ThreadLocal<Object> req = (ThreadLocal<Object>) rq.get(null);
		Map<String, Object> out = new java.util.TreeMap<>();
		for (settlement.entity.ENTITY e : SETT.ENTITIES().getAllEnts()) {
			if (!(e instanceof settlement.entity.humanoid.Humanoid h))
				continue;
			String k = h.race().key + "/" + h.indu().clas().key;
			if (out.containsKey(k))
				continue;
			req.set(h);
			try {
				snake2d.util.datatypes.COORDINATE c = SETT.PATH().finders.finder().findDest(h.tc().x(), h.tc().y(), sh.service().finder, 1000);
				RoomInstance r = c == null ? null : SETT.ROOMS().map.instance.get(c.x(), c.y());
				out.put(k, r == null ? "none" : r.blueprint().key + "@" + r.body().x1() + "," + r.body().y1());
			} finally {
				req.remove();
			}
		}
		return out;
	}

	private ModMods() {}
}
