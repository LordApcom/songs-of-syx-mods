package lordapcom.bridge;

import static lordapcom.bridge.Commands.m;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import game.faction.FACTIONS;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.job.Job;
import settlement.job.JobBuildStructure;
import settlement.job.ROOM_JOBBER;
import settlement.main.SETT;
import settlement.misc.util.FSERVICE;
import settlement.path.finders.SFinderFindable;
import settlement.path.finders.SFinderRoomService;
import settlement.room.infra.gate.ROOM_GATE;
import settlement.room.main.ROOMA;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomInstance;
import settlement.room.main.employment.RoomEmploymentIns;
import settlement.room.main.furnisher.FurnisherItemGroup;
import settlement.room.main.placement.RoomPlacer;
import settlement.stats.STATS;
import settlement.tilemap.floor.Floors.Floor;
import settlement.tilemap.terrain.TBuilding;
import settlement.tilemap.terrain.TFortification;
import settlement.tilemap.terrain.Terrain.TerrainTile;
import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.DIR;
import snake2d.util.datatypes.RECTANGLE;
import snake2d.util.datatypes.Rec;
import view.tool.PLACER_TYPE;
import view.tool.PlacableFixed;
import view.tool.PlacableMulti;

/** Map / room / job commands. Game methods used are cited next to each call. */
final class Build {

	private static final int MAX_W = 200, MAX_H = 120;

	// ---- tool runner: copy of what the drag tool does ----------------------------------------

	/** A growing tile set, like view/tool/PlacerArea (that class is package-private). */
	static final class Area implements AREA {
		private final Set<Integer> tiles = new HashSet<>();
		private final Rec body = new Rec();

		void add(int x, int y) {
			if (SETT.IN_BOUNDS(x, y) && tiles.add(x + y * SETT.TWIDTH))
				body.unify(x, y);
		}

		@Override public boolean is(int tile) { return tiles.contains(tile); }
		@Override public boolean is(int x, int y) { return SETT.IN_BOUNDS(x, y) && tiles.contains(x + y * SETT.TWIDTH); }
		@Override public RECTANGLE body() { return body; }
		@Override public int area() { return tiles.size(); }
	}

	/**
	 * view/tool/PlacableMultiTool.java: updateHovered() paints a SQUARE and expands it through
	 * expandsTo(); place() (line 312) does finishChecking, isPlacable(area), per tile
	 * isPlacable+place, finishPlacing.
	 */
	static Map<String, Object> runTool(PlacableMulti p, int x, int y, int w, int h) {
		Area a = new Area();
		for (int yy = y; yy < y + h; yy++)
			for (int xx = x; xx < x + w; xx++)
				a.add(xx, yy);
		ArrayDeque<int[]> q = new ArrayDeque<>();
		for (int t : new ArrayList<>(a.tiles))
			q.add(new int[] { t % SETT.TWIDTH, t / SETT.TWIDTH });
		while (!q.isEmpty()) {
			int[] c = q.poll();
			for (int i = 0; i < DIR.ALL.size(); i++) {
				DIR d = DIR.ALL.get(i);
				int dx = c[0] + d.x(), dy = c[1] + d.y();
				if (SETT.IN_BOUNDS(dx, dy) && !a.is(dx, dy) && p.expandsTo(c[0], c[1], dx, dy)) {
					a.add(dx, dy);
					q.add(new int[] { dx, dy });
				}
			}
		}
		p.finishChecking(a);
		CharSequence whole = p.isPlacable(a, PLACER_TYPE.SQUARE);
		if (whole != null)
			return m("placed", 0, "rejected", a.area(), "error", whole.toString());
		int placed = 0, rejected = 0;
		String first = null;
		for (int yy = a.body().y1(); yy < a.body().y2(); yy++) {
			for (int xx = a.body().x1(); xx < a.body().x2(); xx++) {
				if (!a.is(xx, yy))
					continue;
				CharSequence e = p.isPlacable(xx, yy, a, PLACER_TYPE.SQUARE);
				if (e != null) {
					rejected++;
					if (first == null)
						first = e.toString();
					continue;
				}
				p.place(xx, yy, a, PLACER_TYPE.SQUARE);
				placed++;
			}
		}
		p.finishPlacing(a);
		Map<String, Object> r = m("placed", placed, "rejected", rejected);
		if (first != null)
			r.put("firstRejection", first.isEmpty() ? "(blocked, no message)" : first);
		return r;
	}

	/** view/tool/PlacableFixedTool.java click(): placableWhole, placable per tile, place per tile, afterPlaced. */
	static CharSequence placeItem(PlacableFixed pp, int x1, int y1) {
		pp.init(x1 + pp.width() / 2, y1 + pp.height() / 2);
		int w = pp.width(), h = pp.height();
		CharSequence e = pp.placableWhole(x1, y1);
		if (e != null)
			return e.length() == 0 ? "(item blocked)" : e;
		for (int dy = 0; dy < h; dy++)
			for (int dx = 0; dx < w; dx++) {
				e = pp.placable(x1 + dx, y1 + dy, dx, dy);
				if (e != null)
					return e.length() == 0 ? "(item blocked at " + (x1 + dx) + "," + (y1 + dy) + ")" : e;
			}
		for (int dy = 0; dy < h; dy++)
			for (int dx = 0; dx < w; dx++)
				pp.place(x1 + dx, y1 + dy, dx, dy);
		pp.afterPlaced(x1, y1);
		return null;
	}

	// ---- rooms --------------------------------------------------------------------------------

	private static Map<Integer, Map<String, Integer>> employeeMap() {
		Map<Integer, Map<String, Integer>> r = new HashMap<>();
		Commands.forHumanoids(h -> {
			RoomInstance ins = STATS.WORK().EMPLOYED.get(h);
			if (ins != null)
				r.computeIfAbsent(ins.index(), k -> new LinkedHashMap<>()).merge(h.race().key + "/" + h.indu().clas().key, 1, Integer::sum);
		});
		return r;
	}

	static String state(Room r) {
		if (r instanceof ROOM_JOBBER)
			return ((ROOM_JOBBER) r).isJobActive() ? "construction" : "planned-dormant"; // ROOM_JOBBER.isJobActive
		if (r.blueprint() == SETT.ROOMS().placement.factory)
			return "embryo";
		if (r instanceof RoomInstance)
			return ((RoomInstance) r).active() ? "active" : "inactive";
		return "other";
	}

	private static Map<String, Object> describe(Room r, Map<Integer, Map<String, Integer>> emp) {
		ROOMA a = (ROOMA) r;
		RECTANGLE b = a.body();
		Map<String, Object> m = m("id", r.index(), "blueprint", r.blueprint().key, "state", state(r), "area", a.area(), "x1", b.x1(),
				"y1", b.y1(), "x2", b.x2() - 1, "y2", b.y2() - 1);
		if (r instanceof ROOM_JOBBER && r.constructor() != null)
			m.put("buildsBlueprint", r.constructor().blue().key);
		if (r instanceof RoomInstance)
			m.put("reachable", ((RoomInstance) r).reachable());
		Map<String, Integer> e = emp.get(r.index());
		m.put("employees", e == null ? new LinkedHashMap<String, Integer>() : e);
		return m;
	}

	static Object rooms() {
		Map<Integer, Map<String, Integer>> emp = employeeMap();
		List<Object> l = new ArrayList<>();
		for (int i = 0; i < SETT.ROOMS().map.max(); i++) {
			Room r = SETT.ROOMS().map.getByIndex(i);
			if (r instanceof ROOMA && ((ROOMA) r).area() > 0)
				l.add(describe(r, emp));
		}
		return m("count", l.size(), "rooms", l);
	}

	static Object room(int x, int y) {
		Room r = SETT.ROOMS().map.get(x, y);
		if (r == null || !(r instanceof ROOMA))
			return m("room", null, "x", x, "y", y);
		Map<Integer, Map<String, Integer>> emp = employeeMap();
		Map<String, Object> m = describe(r, emp);
		m.put("planningModeNow", SETT.JOBS().planMode.is());
		if (r instanceof ROOM_JOBBER)
			m.put("jobsActive", ((ROOM_JOBBER) r).isJobActive());
		if (r instanceof RoomInstance) {
			RoomInstance ri = (RoomInstance) r;
			try {
				RoomEmploymentIns e = ri.employees(); // RoomInstance.java:342
				m.put("employment", m("employed", e.employed(), "target", e.target(), "max", e.max(), "needed", e.needed()));
			} catch (Throwable t) {
				m.put("employment", null);
			}
			int spots = 0, reserved = 0;
			for (int yy = ri.body().y1(); yy < ri.body().y2(); yy++)
				for (int xx = ri.body().x1(); xx < ri.body().x2(); xx++) {
					if (!ri.is(xx, yy))
						continue;
					try {
						SFinderFindable f = ri.blueprint().service(xx, yy);
						if (f instanceof SFinderRoomService) {
							FSERVICE s = ((SFinderRoomService) f).get(xx, yy);
							if (s != null) {
								spots++;
								if (s.findableReservedIs())
									reserved++;
							}
						}
					} catch (Throwable t) {
						// room type without a service finder
					}
				}
			m.put("serviceSpots", m("total", spots, "reserved", reserved));
			List<Object> workers = new ArrayList<>(), present = new ArrayList<>(), heading = new ArrayList<>();
			Commands.forHumanoids(h -> {
				Map<String, Object> hm = m("id", h.id(), "race", h.race().key, "class", h.indu().clas().key, "plan", Commands.plan(h));
				if (STATS.WORK().EMPLOYED.get(h) == ri) {
					workers.add(hm);
				} else if (ri.is(h.tc().x(), h.tc().y())) {
					present.add(hm);
				} else if (h.ai() instanceof AIManager) {
					COORDINATE d = ((AIManager) h.ai()).getDestination();
					if (d != null && ri.is(d.x(), d.y()))
						heading.add(hm);
				}
			});
			m.put("workers", workers);
			m.put("presentNotEmployed", present);
			m.put("headingHere", heading);
		}
		return m;
	}

	// ---- ascii --------------------------------------------------------------------------------

	private static final String LETTERS = "abcdefghiklmnopqrsuvwxyzABCDEFHIJKLMNOPQRSUVWXYZ0123456789";

	static Object ascii(int x0, int y0, int w, int h) {
		w = Math.max(1, Math.min(w, MAX_W));
		h = Math.max(1, Math.min(h, MAX_H));
		Set<Integer> humans = new HashSet<>();
		Commands.forHumanoids(hu -> humans.add(hu.tc().x() + hu.tc().y() * SETT.TWIDTH));
		Map<Integer, Character> letters = new LinkedHashMap<>();
		Set<String> singles = new HashSet<>();
		List<Object> rows = new ArrayList<>();
		for (int y = y0; y < y0 + h; y++) {
			StringBuilder sb = new StringBuilder();
			for (int x = x0; x < x0 + w; x++) {
				if (!SETT.IN_BOUNDS(x, y)) {
					sb.append(' ');
					continue;
				}
				char ch = '.';
				TerrainTile t = SETT.TERRAIN().get(x, y);
				if (t == SETT.TERRAIN().WATER.DEEP) ch = '~';
				else if (t == SETT.TERRAIN().WATER.SHALLOW) ch = '-';
				else if (SETT.TERRAIN().MOUNTAIN.is(x, y)) ch = '^';
				else if (SETT.TERRAIN().TREES.isTree(x, y)) ch = 'T';
				else if ((t instanceof TBuilding.BuildingComponent && !(t instanceof TBuilding.Ceiling)) || t instanceof TFortification.Tile) ch = '#';
				Floor f = SETT.FLOOR().getter.get(x, y);
				if (f != null && f.isRoad) ch = '=';
				if (SETT.JOBS().getter.is(x, y)) {
					Job j = SETT.JOBS().getter.get(x, y);
					if (j != null)
						ch = j == SETT.JOBS().clearss.tunnel ? 't' : 'j';
				}
				Room r = SETT.ROOMS().map.get(x, y);
				if (r != null) {
					if (r.blueprint() == SETT.ROOMS().placement.factory) {
						ch = '+';
					} else if (!(r instanceof ROOMA)) {
						ch = '*';
						singles.add(r.blueprint().key);
					} else if (r.blueprint() instanceof ROOM_GATE) {
						ch = 'G';
						letters.putIfAbsent(r.index(), 'G');
					} else {
						Character c = letters.get(r.index());
						if (c == null) {
							c = letters.size() < LETTERS.length() ? LETTERS.charAt(letters.size()) : '?';
							letters.put(r.index(), c);
						}
						ch = c;
					}
				}
				if (humans.contains(x + y * SETT.TWIDTH))
					ch = '@';
				sb.append(ch);
			}
			rows.add(sb.toString());
		}
		Map<Integer, Map<String, Integer>> emp = new HashMap<>();
		List<Object> legend = new ArrayList<>();
		for (String[] s : new String[][] { { "^", "mountain" }, { "~", "deep water" }, { "-", "shallow water" }, { ".", "ground" },
				{ "=", "road" }, { "t", "tunnel/dig job" }, { "j", "other job" }, { "G", "gate" }, { "@", "humanoid" },
				{ "T", "tree" }, { "#", "wall/fortification" }, { "+", "room being planned (embryo)" }, { "*", "singleton room" } })
			legend.add(m("ch", s[0], "meaning", s[1]));
		for (Map.Entry<Integer, Character> e : letters.entrySet()) {
			Room r = SETT.ROOMS().map.getByIndex(e.getKey());
			if (r != null)
				legend.add(m("ch", String.valueOf(e.getValue()), "room", e.getKey(), "blueprint", r.blueprint().key, "state", state(r)));
		}
		if (!singles.isEmpty())
			legend.add(m("ch", "*", "singletons", new ArrayList<>(singles)));
		return m("x", x0, "y", y0, "w", w, "h", h, "rows", rows, "legend", legend);
	}

	// ---- blueprints / place -------------------------------------------------------------------

	static RoomBlueprintImp blueprint(String key) {
		for (RoomBlueprintImp b : Lists.of(SETT.ROOMS().imps()))
			if (b.key.equalsIgnoreCase(key))
				return b;
		throw new IllegalArgumentException("unknown room blueprint '" + key + "' (see 'blueprints')");
	}

	static Object blueprints(String filter) {
		List<Object> l = new ArrayList<>();
		for (RoomBlueprintImp b : Lists.of(SETT.ROOMS().imps())) {
			if (filter != null && !b.key.toLowerCase().contains(filter.toLowerCase()))
				continue;
			if (b.constructor() == null)
				continue;
			List<Object> groups = new ArrayList<>();
			int gi = 0;
			for (FurnisherItemGroup g : b.constructor().pgroups()) {
				List<Object> dims = new ArrayList<>(); // footprint per size at rotation 0 (FurnisherItemGroup.item(size, rot))
				for (int si = 0; si < g.size(); si++)
					dims.add(g.item(si, 0).width() + "x" + g.item(si, 0).height());
				groups.add(m("i", gi++, "name", g.name().toString(), "min", g.min, "max", g.max, "sizes", g.size(), "rotations", g.rotations(), "dims", dims));
			}
			l.add(m("key", b.key, "name", b.info.name.toString(), "usesArea", b.constructor().usesArea(), "unlocked",
					b.reqs.passes(FACTIONS.player()), "itemGroups", groups));
		}
		return l;
	}

	/**
	 * UI path: UIRoomPlacer.init (view/sett/ui/room/construction/UIRoomPlacer.java:44) -> placer.init(b,0) ->
	 * area tool (PlacerArea) / item tool -> SFrame "construct" button (SFrame.java:81) -> placer.create() ->
	 * Config.deactivateAction (Config.java:142) -> placer.init(null,0).
	 */
	static Object place(String[] a) {
		if (a.length < 4)
			throw new IllegalArgumentException("place <blueprintKey> <x> <y> <w> <h> [item=group,x,y[,rot[,size]] ...] [door=x,y ...]");
		RoomBlueprintImp b = blueprint(a[1]);
		// The build menu decides whether a locked room may be picked (that is where Plan Ahead acts), and the bridge
		// has no menu click to replay, so the lock is only reported here; everything after the pick is the real path.
		boolean lockedVanilla = !b.reqs.passes(FACTIONS.player());
		List<Integer> nums = new ArrayList<>();
		List<int[]> items = new ArrayList<>();
		List<int[]> doors = new ArrayList<>();
		for (int i = 2; i < a.length; i++) {
			if (a[i].startsWith("door=")) {
				String[] p = a[i].substring(5).split(",");
				doors.add(new int[] { Integer.parseInt(p[0]), Integer.parseInt(p[1]) });
			} else if (a[i].startsWith("item=")) {
				String[] p = a[i].substring(5).split(",");
				items.add(new int[] { Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
						p.length > 3 ? Integer.parseInt(p[3]) : 0, p.length > 4 ? Integer.parseInt(p[4]) : 0 });
			} else
				nums.add(Integer.parseInt(a[i]));
		}
		RoomPlacer pl = SETT.ROOMS().placement.placer;
		boolean area = b.constructor().usesArea();
		if (nums.size() < 2 || (area && nums.size() < 4))
			throw new IllegalArgumentException("area rooms need x y w h");
		int x = nums.get(0), y = nums.get(1);
		int w = nums.size() > 2 ? nums.get(2) : 1, h = nums.size() > 3 ? nums.get(3) : 1;
		if (!area && b.constructor().isSpecialAreaPlacable())
			throw new UnsupportedOperationException("special-area single item rooms are not supported by the bridge");
		Map<String, Object> out = m("blueprint", b.key, "lockedVanilla", lockedVanilla);
		try {
			pl.init(b, 0); // RoomPlacer.java:208
			if (area) {
				Map<String, Object> t = runTool((PlacableMulti) pl.area(), x, y, w, h);
				out.put("areaTool", t);
				if (((Number) t.get("placed")).intValue() == 0)
					return fail(out, "no tile of the area could be placed: " + t.get("firstRejection") + t.getOrDefault("error", ""));
			} else if (items.isEmpty()) {
				items.add(new int[] { 0, x, y, 0, 0 });
			}
			for (int[] it : items) {
				PlacableFixed pp = pl.item(it[0]);
				pp.rotSet(it[3]);
				pp.sizeSet(it[4]);
				CharSequence e = placeItem(pp, it[1], it[2]);
				if (e != null)
					return fail(out, "item " + it[0] + " at " + it[1] + "," + it[2] + ": " + e);
			}
			// doorway tool of the room placer (RoomPlacer.placerDoor, :50), one tile per click like the UI
			for (int[] d : doors) {
				Map<String, Object> t = runTool(pl.placerDoor, d[0], d[1], 1, 1);
				if (((Number) t.get("placed")).intValue() == 0)
					return fail(out, "door at " + d[0] + "," + d[1] + ": " + t.get("firstRejection"));
			}
			if (area) {
				int itemTiles = 0; // how many tiles of the plan hold an item (fData.item), to see whether items took
				for (int yy = y; yy < y + h; yy++)
					for (int xx = x; xx < x + w; xx++)
						if (SETT.ROOMS().fData.item.get(xx, yy) != null)
							itemTiles++;
				out.put("itemTiles", itemTiles);
				// The placer caches its item counts and costs once per game tick (UtilStats/UtilExtraCost "tick"). In the
				// UI the construct click lands on a later frame than the item clicks; here it is the same tick, so drop
				// the caches the way RoomPlacer.init does (resources.updatee(), RoomPlacer.java:205).
				Object stats = vizardalpha.polyphon.api.Shadow.get(pl, "resources");
				vizardalpha.polyphon.api.Shadow.invoke(stats, "updatee");
				vizardalpha.polyphon.api.Shadow.set(pl.cost(), "tick", Integer.valueOf(-1));
				CharSequence prob = pl.createProblem(); // SFrame.java:94
				if (prob != null)
					return fail(out, prob.toString());
				CharSequence warn = pl.createWarning(); // UI asks yes/no; we answer yes
				if (warn != null)
					out.put("warning", warn.toString());
				pl.create(); // SFrame.java:81
			}
		} finally {
			pl.init(null, 0); // Config.deactivateAction
		}
		Map<Integer, Map<String, Integer>> emp = new HashMap<>();
		Set<Integer> seen = new HashSet<>();
		List<Object> created = new ArrayList<>();
		int hh = area ? h : 6, ww = area ? w : 6;
		for (int yy = y - (area ? 0 : 3); yy < y - (area ? 0 : 3) + hh; yy++)
			for (int xx = x - (area ? 0 : 3); xx < x - (area ? 0 : 3) + ww; xx++) {
				Room r = SETT.IN_BOUNDS(xx, yy) ? SETT.ROOMS().map.get(xx, yy) : null;
				if (r instanceof ROOMA && r.blueprint() != SETT.ROOMS().placement.factory && seen.add(r.index()))
					created.add(describe(r, emp));
			}
		out.put("created", created);
		out.put("planningMode", SETT.JOBS().planMode.is());
		return out;
	}

	private static Object fail(Map<String, Object> out, String msg) {
		out.put("error", msg);
		throw new IllegalStateException(msg + " | details: " + Json.write(out));
	}

	// ---- jobs ---------------------------------------------------------------------------------

	private static Map<String, PlacableMulti> tools() {
		Map<String, PlacableMulti> t = new LinkedHashMap<>();
		var j = SETT.JOBS();
		t.put("clear", j.tool_clear);
		t.put("dormant", j.tool_dormant);
		t.put("activate", j.tool_activate);
		t.put("tunnel", j.clearss.tunnel.placer());
		t.put("stone", j.clearss.stone.placer());
		t.put("wood", j.clearss.wood.placer());
		t.put("food", j.clearss.food.placer());
		t.put("water", j.clearss.water.placer());
		t.put("structure", j.clearss.structure.placer());
		t.put("cavefill", j.clearss.caveFill.placer());
		t.put("road", roadKey(SETT.FLOOR().defaultRoad.key));
		List<Job> all = new ArrayList<>();
		for (Job r : j.roads.all)
			all.add(r);
		for (JobBuildStructure s : j.build_structure) {
			all.add(s.wall);
			all.add(s.ceiling);
		}
		for (Job f : j.build_fort.all)
			all.add(f);
		for (Job f : j.fences)
			all.add(f);
		for (Job x : all)
			t.put(x.key().toLowerCase(), x.placer());
		return t;
	}

	private static PlacableMulti roadKey(String floorKey) {
		for (Job j : Lists.of(SETT.JOBS().roads.all))
			if (j.key().equalsIgnoreCase("JOB_ROAD_" + floorKey))
				return j.placer();
		throw new IllegalStateException("default road job not found");
	}

	static Object job(String[] a) {
		Map<String, PlacableMulti> t = tools();
		if (a.length < 2 || a[1].equals("list"))
			return new ArrayList<>(t.keySet());
		String k = a[1].toLowerCase();
		PlacableMulti p = t.get(k);
		if (p == null && k.startsWith("road:"))
			p = roadKey(a[1].substring(5));
		if (p == null)
			p = t.get("job_" + k);
		if (p == null)
			throw new IllegalArgumentException("unknown job '" + k + "' (job list)");
		boolean ow = a.length > 6 && a[6].equals("overwrite");
		boolean old = Job.overwrite; // the UI overwrite toggle (settlement/job/Placer.java)
		Job.overwrite = ow;
		try {
			Map<String, Object> r = runTool(p, Commands.i(a, 2), Commands.i(a, 3), Commands.i(a, 4), Commands.i(a, 5));
			r.put("planningMode", SETT.JOBS().planMode.is());
			return r;
		} finally {
			Job.overwrite = old;
		}
	}

	/** view/sett/ui/bottom/Options.java:82 toggles JOBS().planMode. */
	static Object plan(String[] a) {
		if (a.length > 1)
			SETT.JOBS().planMode.set(a[1].equals("on"));
		return m("planMode", SETT.JOBS().planMode.is());
	}
}
