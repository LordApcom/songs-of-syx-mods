package lordapcom.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import snake2d.util.gui.clickable.CLICKABLE;
import view.main.VIEW;
import view.tool.PlacableFixed;
import view.tool.PlacableMulti;
import vizardalpha.polyphon.api.Shadow;

/**
 * The game's own developer panels (the cheats the dev uses): every button registered with IDebugPanel (global),
 * IDebugPanelSett (settlement) and IDebugPanelWorld (world map). Running one clicks that exact button.
 */
final class ModDebug {

	static void register() {
		Registry.add("invasion", "invasion - settlement invasions in progress: state, timer, spot size, enemies on the map", ModDebug::invasion);
		Registry.add("debug", "debug [global|sett|world] [filter] | debug run <panel> <name...> | debug tool <x> <y> [w h]"
			+ " - list / click the game's own developer-panel buttons (invasions, riots, events, finish, spawn...); 'tool'"
			+ " applies a debug placer chosen with 'run' to an area", ModDebug::debug);
	}

	/**
	 * The panel instances keep their buttons in IDebugPanelAbs.current.items (view/interrupter/IDebugPanelAbs.java:117);
	 * the static registries are emptied once the panel is built.
	 */
	@SuppressWarnings("unchecked")
	private static TreeMap<CharSequence, CLICKABLE> panel(String p) {
		Object inst;
		switch (p) {
		case "global": inst = VIEW.inters().debugpanel; break; // view/main/Interrupters.java:36
		case "sett": inst = VIEW.s().debug; break;               // view/sett/SettView.java:84
		case "world": inst = VIEW.world().debug; break;          // view/world/WorldView.java:75
		default: throw new IllegalArgumentException("panel must be global, sett or world");
		}
		Object type = Shadow.get(inst, "current");
		return (TreeMap<CharSequence, CLICKABLE>) (Object) Shadow.get(type, "items");
	}

	private static Object debug(String[] a) throws Exception {
		String sub = a.length > 1 ? a[1].toLowerCase() : "list";
		if (sub.equals("run")) {
			if (a.length < 4)
				throw new IllegalArgumentException("debug run <panel> <name...>");
			String name = String.join(" ", java.util.Arrays.copyOfRange(a, 3, a.length));
			TreeMap<CharSequence, CLICKABLE> h = panel(a[2]);
			CLICKABLE c = null;
			for (Map.Entry<CharSequence, CLICKABLE> e : h.entrySet())
				if (e.getKey().toString().equalsIgnoreCase(name))
					c = e.getValue();
			if (c == null)
				throw new IllegalArgumentException("no button '" + name + "' in " + a[2] + " (debug " + a[2] + ")");
			// game path: ClickableAbs.click() (snake2d/util/gui/clickable/CLICKABLE.java:155) needs the button hovered,
			// as it is under the mouse when the dev clicks it
			Shadow.set(c, "isHovered", Boolean.TRUE);
			boolean done = c.click();
			Shadow.set(c, "isHovered", Boolean.FALSE);
			return Commands.m("clicked", done, "button", name, "toggleNow", c.selectedIs());
		}
		if (sub.equals("tool")) {
			if (a.length < 4)
				throw new IllegalArgumentException("debug tool <x> <y> [w h]");
			Object pl = Shadow.get(VIEW.s().tools.placer, "placer"); // the placer the debug button activated
			int x = Commands.i(a, 2), y = Commands.i(a, 3);
			if (pl instanceof PlacableMulti)
				return Build.runTool((PlacableMulti) pl, x, y, a.length > 4 ? Commands.i(a, 4) : 1, a.length > 5 ? Commands.i(a, 5) : 1);
			if (pl instanceof PlacableFixed) {
				CharSequence e = Build.placeItem((PlacableFixed) pl, x, y);
				return Commands.m("placed", e == null, "error", e == null ? null : e.toString());
			}
			throw new IllegalStateException("no debug placer active (debug run <panel> <placer button> first)");
		}
		// list
		String p = sub.equals("list") ? null : sub;
		String filter = a.length > 2 ? a[2].toLowerCase() : null;
		Map<String, Object> out = Commands.m();
		for (String k : new String[] { "global", "sett", "world" }) {
			if (p != null && !p.equals(k))
				continue;
			List<Object> l = new ArrayList<>();
			for (Map.Entry<CharSequence, CLICKABLE> e : panel(k).entrySet()) {
				String n = e.getKey().toString();
				if (filter == null || n.toLowerCase().contains(filter))
					l.add(n);
			}
			out.put(k, l);
		}
		return out;
	}

	/** Read-only view of settlement/battle/invasion/Invador.active (private) and each Invasion's state machine. */
	private static Object invasion(String[] a) {
		java.util.List<Object> l = new ArrayList<>();
		snake2d.util.sets.LIST<?> act = Shadow.get(settlement.main.SETT.INVADOR(), "active");
		for (Object inv : Lists.of(act)) {
			Object spot = Shadow.get(inv, "spot");
			Object body = Shadow.get(spot, "body");
			l.add(Commands.m("state", String.valueOf((Object) Shadow.get(inv, "state")), "timer", Shadow.get(inv, "timer"),
				"spotTiles", ((snake2d.util.datatypes.RECTANGLE) body).width() * ((snake2d.util.datatypes.RECTANGLE) body).height(),
				"spotX", ((snake2d.util.datatypes.RECTANGLE) body).cX(), "spotY", ((snake2d.util.datatypes.RECTANGLE) body).cY()));
		}
		return Commands.m("active", l, "enemiesOnMap", settlement.stats.STATS.POP().pop(init.type.HTYPES.ENEMY()),
			"secondsPerDay", game.time.TIME.secondsPerDay());
	}

	private ModDebug() {}
}
