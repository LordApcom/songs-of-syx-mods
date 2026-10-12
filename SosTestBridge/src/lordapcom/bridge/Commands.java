package lordapcom.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import game.GAME;
import game.faction.FACTIONS;
import game.save.GameLoader;
import game.time.TIME;
import init.paths.PATHS;
import init.race.RACES;
import init.race.Race;
import init.tech.TECH;
import init.tech.TECH.TechRequirement;
import init.tech.TECHS;
import init.type.HCLASS;
import init.type.HCLASSES;
import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import settlement.main.SETT;
import settlement.room.home.HOME;
import settlement.room.main.Room;
import settlement.room.main.RoomInstance;
import settlement.stats.POP;
import settlement.stats.STATS;
import snake2d.CORE;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.sprite.text.Str;
import view.main.VIEW;

/** Command table. Every method here runs on the game update thread (see Bridge). */
final class Commands {

	/** UI speed buttons: GameSpeed.speed1/speed2/speed3/speed4 (game/GameSpeed.java:20-25). */
	static final double[] SPEEDS = { 1, 5, 25, 250 };
	private static view.sett.ui.room.construction.UIRoomPlacer roomPlacer; // created on first "ui build"

	static Map<String, Object> m(Object... kv) {
		Map<String, Object> r = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2)
			r.put((String) kv[i], kv[i + 1]);
		return r;
	}

	static Object exec(String[] a) throws Exception {
		String c = a[0].toLowerCase();
		switch (c) {
		case "ping": return m("pong", true, "epochMs", System.currentTimeMillis());
		case "help": return "ping status speed run ascii rooms room blueprints place job plan tech humans eval save saves load look mouse dismiss | menu: newgame load continue quit | worldgen: wg ... | landing: landing";
		case "status": return status();
		case "speed": return speed(a);
		case "run": return run(a);
		case "ascii": return Build.ascii(i(a, 1), i(a, 2), i(a, 3), i(a, 4));
		case "rooms": return Build.rooms();
		case "room": return Build.room(i(a, 1), i(a, 2));
		case "blueprints": return Build.blueprints(a.length > 1 ? a[1] : null);
		case "place": return Build.place(a);
		case "job": return Build.job(a);
		case "plan": return Build.plan(a);
		case "tech": return tech(a);
		case "humans": return humans(a.length > 1 ? a[1] : null);
		case "eval": return eval(a);
		case "save": return save(a);
		case "saves": return PATHS.local().save().getFiles();
		case "load": return load(a);
		case "tile": { // what the game holds on one tile: terrain, mountain flag, job, room
			int x = i(a, 1), y = i(a, 2);
			settlement.job.Job j = SETT.JOBS().getter.get(x, y);
			settlement.room.main.Room r = SETT.ROOMS().map.get(x, y);
			return m("terrain", SETT.TERRAIN().get(x, y).key(), "mountain", SETT.TERRAIN().MOUNTAIN.is(x, y),
					"job", j == null ? null : j.key(), "room", r == null ? null : r.blueprint().key, "floor",
					SETT.FLOOR().getter.get(x, y) == null ? null : SETT.FLOOR().getter.get(x, y).key);
		}
		case "look": { // camera only: GameWindow.centerAtTile / setZoomout (view/subview/GameWindow.java:530,180)
			view.subview.GameWindow w = VIEW.s().getWindow();
			if (a.length > 3)
				w.setZoomout(i(a, 3));
			w.centerAtTile(i(a, 1), i(a, 2));
			return m("x", i(a, 1), "y", i(a, 2), "zoomout", w.zoomout());
		}
		case "dismiss": { // "click to continue" after a load: the click handler of ILoadScreen (view/interrupter/ILoadScreen.java:46)
			boolean was = VIEW.inters().load.isActivated();
			if (was)
				VIEW.inters().load.deactivate();
			boolean msg = VIEW.messages().activated(); // Messages.hideAll (view/ui/message/Messages.java:264), the popup's close button
			if (msg)
				VIEW.messages().hideAll();
			return m("loadScreenWasShown", was, "messageWasShown", msg);
		}
		case "ui": { // open a game panel the way its buttons do (camera/UI only)
			String what = a.length > 1 ? a[1] : "";
			if (what.equals("workprio")) { // view/sett/ui/room/UIRooms.java:72 VIEW.s().panels.add(main.work, true)
				Object work = vizardalpha.polyphon.api.Shadow.get(VIEW.s().ui.rooms.main(), "work");
				VIEW.s().panels.add((view.interrupter.ISidePanel) work, true);
				return m("opened", "workprio");
			}
			if (what.equals("rooms")) {
				VIEW.s().panels.add(VIEW.s().ui.rooms.main(), true);
				return m("opened", "rooms");
			}
			if (what.equals("close")) {
				VIEW.s().panels.clear();
				return m("closed", true);
			}
			if (what.equals("build") && a.length > 2) { // the build menu's button: view/sett/ui/bottom/BuildMain -> UIRoomPlacer.init
				settlement.room.main.RoomBlueprintImp b = null;
				for (settlement.room.main.RoomBlueprintImp x : Lists.of(SETT.ROOMS().imps()))
					if (x.key.equalsIgnoreCase(a[2]))
						b = x;
				if (b == null)
					throw new IllegalArgumentException("unknown blueprint " + a[2]);
				if (roomPlacer == null)
					roomPlacer = new view.sett.ui.room.construction.UIRoomPlacer();
				roomPlacer.init(b, -1, -1);
				return m("tool", b.key);
			}
			if (what.equals("job") && a.length > 2) { // a job tool (roads, clear...), as from the build menu
				view.tool.PlacableMulti p = null;
				for (settlement.job.Job j : Lists.of(SETT.JOBS().roads.all))
					if (j.key().equalsIgnoreCase(a[2]) || j.key().equalsIgnoreCase("JOB_ROAD_" + a[2]))
						p = j.placer();
				if (p == null)
					throw new IllegalArgumentException("unknown road job " + a[2]);
				VIEW.s().tools.place(p);
				return m("tool", a[2]);
			}
			throw new IllegalArgumentException("ui workprio | ui rooms | ui close | ui build <blueprint> | ui job <roadKey>");
		}
		case "prompt": { // the game's yes/no question box (view/interrupter/IPromtYesNO.java); answering = clicking its button
			view.interrupter.IPromtYesNO q = VIEW.inters().yesNo;
			if (!q.isActivated())
				return m("open", false);
			Object txt = vizardalpha.polyphon.api.Shadow.get(q, "text");
			String text = String.valueOf((Object) vizardalpha.polyphon.api.Shadow.invoke(txt, "text"));
			if (a.length < 2)
				return m("open", true, "text", text);
			snake2d.util.gui.clickable.CLICKABLE butt = vizardalpha.polyphon.api.Shadow.get(q, a[1].equalsIgnoreCase("yes") ? "yes" : "no");
			vizardalpha.polyphon.api.Shadow.set(butt, "isHovered", Boolean.TRUE);
			boolean clicked = butt.click();
			vizardalpha.polyphon.api.Shadow.set(butt, "isHovered", Boolean.FALSE);
			return m("answered", a[1].toLowerCase(), "clicked", clicked, "text", text, "stillOpen", q.isActivated());
		}
		case "mouse": { // tile under the cursor, as the tools read it (GameWindow.tile(), :575)
			COORDINATE t = VIEW.s().getWindow().tile();
			return m("x", t.x(), "y", t.y());
		}
		case "commands": return Registry.HELP;
		default:
			Registry.Cmd x = Registry.CMDS.get(c);
			if (x != null)
				return x.run(a);
			throw new IllegalArgumentException("unknown command: " + c + " (try help / commands)");
		}
	}

	static int i(String[] a, int idx) {
		if (idx >= a.length)
			throw new IllegalArgumentException("missing argument #" + idx);
		return Integer.parseInt(a[idx]);
	}

	// ---- status -------------------------------------------------------------------------------

	static Map<String, Object> status() {
		Map<String, Object> r = m();
		boolean sett = SETT.exists();
		r.put("settlement", sett);
		r.put("second", TIME.currentSecond());
		r.put("day", TIME.days().bitsSinceStart());
		r.put("hour", TIME.hours().bitCurrent());
		r.put("year", TIME.years().bitsSinceStart());
		r.put("secondsPerDay", TIME.secondsPerDay());
		r.put("speed", GAME.SPEED.speedTarget());
		r.put("speedActual", GAME.SPEED.speed());
		r.put("paused", GAME.SPEED.isPaused());
		r.put("runActive", Bridge.run != null);
		r.put("loadScreen", VIEW.inters().load.isActivated());
		r.put("popup", VIEW.messages().activated());
		r.put("question", VIEW.inters().yesNo.isActivated()); // a yes/no box waits for the player: answer with 'prompt yes|no'

		if (sett) {
			r.put("planMode", SETT.JOBS().planMode.is());
			r.put("map", m("w", SETT.TWIDTH, "h", SETT.THEIGHT));
			Map<String, Object> byRace = m();
			for (Race race : Lists.of(RACES.all())) {
				Map<String, Object> byClass = m();
				for (HCLASS cl : Lists.of(HCLASSES.ALL())) {
					int n = POP.pop(cl, race);
					if (n > 0)
						byClass.put(cl.key, n);
				}
				if (!byClass.isEmpty())
					byRace.put(race.key, byClass);
			}
			r.put("pop", m("total", POP.pop(), "byRace", byRace));
		}
		return r;
	}

	// ---- speed / run --------------------------------------------------------------------------

	private static double speedOf(String s) {
		switch (s) {
		case "half": return 0.25; // GameSpeed.speed05
		case "0": return 0;
		case "1": return 1;
		case "2": return 5;
		case "3": return 25;
		case "4": return 250;
		default: throw new IllegalArgumentException("speed: 0|1|2|3|4|half");
		}
	}

	private static Object speed(String[] a) {
		double v = speedOf(a.length > 1 ? a[1] : "");
		Bridge.Run r = Bridge.run;
		if (r != null) { // a manual speed change aborts the run
			Bridge.run = null;
			Bridge.reply(r.id, true, m("cancelled", true, "elapsedGameSeconds", r.elapsed), null);
		}
		GAME.SPEED.speedSet(v); // same call the speed buttons make: game/GameSpeed.java:speedSet
		return status();
	}

	private static Object run(String[] a) {
		if (Bridge.run != null)
			throw new IllegalStateException("a run is already in progress");
		double sec = Double.parseDouble(a[1]);
		double cap = a.length > 2 ? speedOf(a[2]) : 25;
		if (cap <= 0)
			throw new IllegalArgumentException("run speed must be 1..4");
		if (sec <= 0)
			return status();
		Bridge.run = new Bridge.Run(Bridge.currentId, sec, cap);
		GAME.SPEED.speedSet(cap);
		return Bridge.DEFERRED;
	}

	// ---- tech ---------------------------------------------------------------------------------

	private static Object tech(String[] a) {
		if (a.length < 2 || a[1].equals("list")) {
			List<Object> l = new ArrayList<>();
			for (TECH t : Lists.of(TECHS.ALL())) {
				int lv = FACTIONS.player().tech().level(t);
				l.add(m("key", t.key, "name", t.name().toString(), "level", lv, "max", t.levelMax, "unlocked", lv > 0,
						"canUnlockNext", FACTIONS.player().tech().canUnlockNext(t)));
			}
			return l;
		}
		if (!a[1].equals("unlock") || a.length < 3)
			throw new IllegalArgumentException("tech list | tech unlock <key> [force]");
		TECH t = null;
		for (TECH x : Lists.of(TECHS.ALL()))
			if (x.key.equalsIgnoreCase(a[2]))
				t = x;
		if (t == null)
			throw new IllegalArgumentException("unknown tech " + a[2]);
		if (FACTIONS.player().tech().level(t) >= t.levelMax)
			return m("key", t.key, "level", t.levelMax, "already", true);
		boolean force = a.length > 3 && a[3].equals("force");
		// view/ui/tech/Prompt.java:37 - the UI refuses unless affordable (or developer mode)
		if (!force && !FACTIONS.player().tech().canUnlockNext(t))
			throw new IllegalStateException("cannot unlock (not affordable / prerequisites); use 'force' to behave like developer mode");
		unlock(t, FACTIONS.player().tech().level(t) + 1);
		return m("key", t.key, "level", FACTIONS.player().tech().level(t));
	}

	/** Same recursion as view/ui/tech/Prompt.java:94 pUnlock. */
	private static void unlock(TECH t, int level) {
		for (TechRequirement r : t.requires())
			if (FACTIONS.player().tech().level(r.tech) < r.level)
				unlock(r.tech, r.level);
		FACTIONS.player().tech().levelSet(t, level);
	}

	// ---- humans -------------------------------------------------------------------------------

	interface HumanVisitor {
		void visit(Humanoid h);
	}

	static void forHumanoids(HumanVisitor v) {
		for (ENTITY e : SETT.ENTITIES().getAllEnts()) {
			if (e instanceof Humanoid && !e.isRemoved())
				v.visit((Humanoid) e);
		}
	}

	static String plan(Humanoid h) {
		return h.ai() instanceof AIManager && ((AIManager) h.ai()).plan() != null ? ((AIManager) h.ai()).plan().key : null;
	}

	private static Object humans(String race) {
		List<Object> out = new ArrayList<>();
		int[] total = { 0 };
		forHumanoids(h -> {
			if (race != null && !h.race().key.equalsIgnoreCase(race))
				return;
			total[0]++;
			if (out.size() >= 3000)
				return;
			Map<String, Object> r = m("id", h.id(), "race", h.race().key, "class", h.indu().clas().key, "type",
					h.indu().hType().key, "x", h.tc().x(), "y", h.tc().y(), "plan", plan(h));
			if (h.ai() instanceof AIManager) {
				AIManager ai = (AIManager) h.ai();
				if (ai.plansub() != null)
					r.put("sub", ai.plansub().key);
				Str s = new Str(120);
				ai.getOccupation(h, s);
				r.put("doing", s.toString());
			}
			RoomInstance w = STATS.WORK().EMPLOYED.get(h);
			r.put("workRoom", w == null ? null : w.index());
			HOME home = STATS.HOME().GETTER.get(h, null);
			COORDINATE hc = STATS.HOME().GETTER.hCoo(h);
			if (home != null && hc != null) {
				Room hr = SETT.ROOMS().map.get(hc.x(), hc.y());
				r.put("homeRoom", hr == null ? null : hr.index());
				r.put("homeTile", m("x", hc.x(), "y", hc.y()));
			}
			out.add(r);
		});
		return m("count", total[0], "humans", out, "truncated", total[0] > out.size());
	}

	// ---- eval ---------------------------------------------------------------------------------

	private static Object eval(String[] a) throws Exception {
		if (a.length < 3)
			throw new IllegalArgumentException("eval <Class> <staticMethodOrField> [args...]");
		Class<?> c = Class.forName(a[1]);
		String name = a[2];
		int n = a.length - 3;
		if (n == 0) {
			for (Field f : c.getDeclaredFields()) {
				if (f.getName().equals(name) && Modifier.isStatic(f.getModifiers())) {
					f.setAccessible(true);
					return norm(f.get(null), 0);
				}
			}
		}
		for (Method me : c.getDeclaredMethods()) {
			if (!me.getName().equals(name) || !Modifier.isStatic(me.getModifiers()) || me.getParameterCount() != n)
				continue;
			Object[] args = new Object[n];
			boolean ok = true;
			for (int k = 0; k < n && ok; k++) {
				args[k] = conv(a[3 + k], me.getParameterTypes()[k]);
				ok = args[k] != null;
			}
			if (!ok)
				continue;
			me.setAccessible(true);
			return norm(me.invoke(null, args), 0);
		}
		throw new NoSuchMethodException("no static field/method " + name + " with " + n + " string/int/double/boolean args");
	}

	private static Object conv(String s, Class<?> t) {
		try {
			// typed tokens for test calls: room:x,y  bools:1,0,1  ints:3,4
			if (s.startsWith("room:") && Room.class.isAssignableFrom(t)) {
				String[] xy = s.substring(5).split(",");
				Object r = SETT.ROOMS().map.instance.get(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]));
				return t.isInstance(r) ? r : null;
			}
			if (s.startsWith("bools:") && t == boolean[].class) {
				String[] v = s.substring(6).split(",");
				boolean[] r = new boolean[v.length];
				for (int k = 0; k < v.length; k++)
					r[k] = v[k].equals("1") || v[k].equalsIgnoreCase("true");
				return r;
			}
			if (s.startsWith("ints:") && t == int[].class) {
				String[] v = s.substring(5).split(",");
				int[] r = new int[v.length];
				for (int k = 0; k < v.length; k++)
					r[k] = Integer.parseInt(v[k]);
				return r;
			}
			if (t == String.class || t == CharSequence.class || t == Object.class) return s;
			if (t == int.class || t == Integer.class) return Integer.valueOf(s);
			if (t == long.class || t == Long.class) return Long.valueOf(s);
			if (t == double.class || t == Double.class) return Double.valueOf(s);
			if (t == float.class || t == Float.class) return Float.valueOf(s);
			if (t == boolean.class || t == Boolean.class) return Boolean.valueOf(s);
		} catch (NumberFormatException e) {
			// fall through
		}
		return null;
	}

	static Object norm(Object o, int depth) {
		if (o == null || o instanceof Boolean || o instanceof Number || o instanceof String)
			return o;
		if (depth < 3 && o.getClass().isArray()) {
			List<Object> l = new ArrayList<>();
			int len = java.lang.reflect.Array.getLength(o);
			for (int k = 0; k < Math.min(len, 200); k++)
				l.add(norm(java.lang.reflect.Array.get(o, k), depth + 1));
			return l;
		}
		if (depth < 3 && o instanceof Iterable) {
			List<Object> l = new ArrayList<>();
			for (Object x : (Iterable<?>) o) {
				if (l.size() >= 200)
					break;
				l.add(norm(x, depth + 1));
			}
			return l;
		}
		return o.toString();
	}

	// ---- save / load --------------------------------------------------------------------------

	private static String saveName(String[] a) {
		if (a.length < 2 || !a[1].matches("[A-Za-z0-9_\\-]{1,60}"))
			throw new IllegalArgumentException("name must match [A-Za-z0-9_-]{1,60}");
		return a[1];
	}

	private static Object save(String[] a) {
		String name = saveName(a);
		String id = Bridge.currentId;
		// run on the tick boundary (GAME.addAfterUpdate), not in the middle of the resource loop
		Bridge.defer(() -> {
			try {
				if (!VIEW.canSave()) {
					Bridge.reply(id, false, null, "game cannot save right now (VIEW.canSave() is false)");
					return;
				}
				Path p = GAME.saver().save(name); // game/save/GameSaver.java:49
				if (p == null)
					Bridge.reply(id, false, null, "GameSaver.save returned null");
				else
					Bridge.reply(id, true, m("path", p.toString()), null);
			} catch (Throwable t) {
				Bridge.reply(id, false, null, t.toString());
			}
		});
		return Bridge.DEFERRED;
	}

	private static Object load(String[] a) {
		String name = saveName(a);
		if (!PATHS.local().save().exists(name))
			throw new IllegalArgumentException("no such save (see 'saves')");
		Bridge.pendingLoadId = Bridge.currentId;
		// same call as GameLoader.quickload() (game/save/GameLoader.java:90); the switch happens after this frame
		CORE.setCurrentState(new GameLoader(PATHS.local().save().get(name)));
		return Bridge.DEFERRED;
	}
}
