package lordapcom.wsf;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import game.GAME;
import init.type.HGROUP;
import init.type.WGROUP;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.main.employment.RoomEmployment;
import settlement.stats.STATS;
import snake2d.LOG;
import snake2d.util.sets.LIST;

/**
 * All filter state. A filter is a bitset over HGROUP (species x citizen/slave); null means "everyone".
 * One Entry per room instance, holding an optional worker filter and an optional user filter.
 */
final class Filters {

	static final class Entry {
		final int[] w, u;
		Entry(int[] w, int[] u) {
			this.w = w;
			this.u = u;
		}
	}

	private static final ConcurrentHashMap<RoomInstance, Entry> MAP = new ConcurrentHashMap<>();
	/** Fast-path flags: when false the hooks do nothing. */
	static volatile boolean anyW, anyU;
	private static volatile int version;
	/** Humanoid whose AI is currently choosing a plan (set by PlanMixin). */
	static final ThreadLocal<Humanoid> REQ = new ThreadLocal<>();
	private static int errs;

	private Filters() {
	}

	static void err(String where, Throwable t) {
		if (errs++ < 5)
			LOG.err("[WSF] " + where + ": " + t);
	}

	// ---- bit helpers ----

	static int words() {
		return (HGROUP.all().size() + 31) >>> 5;
	}

	static boolean bit(int[] b, int i) {
		return (b[i >>> 5] & (1 << (i & 31))) != 0;
	}

	static boolean allows(int[] bits, HGROUP g) {
		return bits == null || g == null || bit(bits, g.index);
	}

	/** Returns null when every group is allowed (no filter needed). */
	static int[] normalize(boolean[] sel) {
		int[] b = new int[words()];
		boolean all = true;
		for (int i = 0; i < sel.length; i++) {
			if (sel[i])
				b[i >>> 5] |= 1 << (i & 31);
			else
				all = false;
		}
		return all ? null : b;
	}

	// ---- queries ----

	static Entry get(RoomInstance ins) {
		return MAP.get(ins);
	}

	static boolean mayWork(Humanoid h, RoomInstance ins) {
		Entry e = MAP.get(ins);
		if (e != null && !allows(e.w, HGROUP.get(h)))
			return false;
		return gate(h, ins);
	}

	static boolean mayUse(RoomInstance ins, Humanoid h) {
		Entry e = MAP.get(ins);
		return e == null || allows(e.u, HGROUP.get(h));
	}

	/** True when the worker hooks have something to do (a filter exists or the optional gate mod is present). */
	static boolean workHooksOn() {
		return anyW || gateOn();
	}

	// ---- optional SpeciesCustomsGate integration (reflection, checked once) ----

	private static Method gate;
	private static boolean gateChecked;

	private static synchronized boolean gateOn() {
		if (!gateChecked) {
			gateChecked = true;
			String cn = "lordapcom.gate.GateApi";
			ClassLoader[] ls = { Filters.class.getClassLoader(), Thread.currentThread().getContextClassLoader(),
					ClassLoader.getSystemClassLoader() };
			for (ClassLoader l : ls) {
				try {
					gate = Class.forName(cn, false, l).getMethod("canReach", Humanoid.class, int.class, int.class);
					break;
				} catch (Throwable t) {
					// not present in this loader
				}
			}
		}
		return gate != null;
	}

	private static boolean gate(Humanoid h, RoomInstance ins) {
		if (!gateOn())
			return true;
		try {
			return (Boolean) gate.invoke(null, h, ins.mX(), ins.mY());
		} catch (Throwable t) {
			err("gate", t);
			return true;
		}
	}

	// ---- changing filters ----

	static void set(RoomInstance ins, boolean workers, int[] bits) {
		Entry o = MAP.get(ins);
		int[] w = o == null ? null : o.w;
		int[] u = o == null ? null : o.u;
		if (Arrays.equals(workers ? w : u, bits))
			return;
		if (workers)
			w = bits;
		else
			u = bits;
		if (w == null && u == null)
			MAP.remove(ins);
		else
			MAP.put(ins, new Entry(w, u));
		refresh();
		if (workers) {
			fire(ins, bits);
			SETT.ROOMS().employment.setTargets();
		}
	}

	private static void fire(RoomInstance ins, int[] bits) {
		ArrayList<Humanoid> l = new ArrayList<>();
		for (Humanoid h : Lists.of(ins.employees().employees()))
			l.add(h);
		for (Humanoid h : l) {
			if (!allows(bits, HGROUP.get(h))) {
				STATS.WORK().EMPLOYED.set(h, null);
				h.interrupt();
			}
		}
	}

	private static void refresh() {
		boolean w = false, u = false;
		for (Entry e : MAP.values()) {
			w |= e.w != null;
			u |= e.u != null;
		}
		anyW = w;
		anyU = u;
		version++;
	}

	static void clear() {
		MAP.clear();
		refresh();
	}

	static void prune() {
		boolean changed = false;
		for (RoomInstance ins : MAP.keySet()) {
			if (!ins.exists()) {
				MAP.remove(ins);
				changed = true;
			}
		}
		if (changed)
			refresh();
	}

	// ---- persistence helpers ----

	static ArrayList<Map.Entry<RoomInstance, Entry>> live() {
		ArrayList<Map.Entry<RoomInstance, Entry>> l = new ArrayList<>();
		for (Map.Entry<RoomInstance, Entry> e : MAP.entrySet())
			if (e.getKey().exists())
				l.add(e);
		return l;
	}

	static void put(RoomInstance ins, int[] w, int[] u) {
		MAP.put(ins, new Entry(w, u));
		refresh();
	}

	// ---- effective worker priority (see PrioMixin) ----

	private static LIST<RoomEmployment> ownersFor;
	private static final IdentityHashMap<Object, RoomEmployment> owners = new IdentityHashMap<>();
	private static byte[][] cache; // 0 unknown, 1 open, 2 blocked
	private static int cacheVer = -1, cacheAt;

	/**
	 * A species that no active instance of a workplace type accepts gets priority 0 there, so the vanilla
	 * allocator stops reserving job slots for it and hands them to species that can actually work.
	 */
	static int effectivePrio(Object prio, int vanilla, WGROUP g) {
		if (vanilla == 0 || g == null || !anyW)
			return vanilla;
		LIST<RoomEmployment> all = SETT.ROOMS().employment.ALL();
		if (all != ownersFor) {
			ownersFor = all;
			owners.clear();
			for (RoomEmployment e : Lists.of(all))
				owners.put(e.priorities, e);
			cache = null;
		}
		RoomEmployment e = owners.get(prio);
		if (e == null)
			return vanilla;
		int now = GAME.updateI();
		if (cache == null || cacheVer != version || now - cacheAt > 120 || now < cacheAt) {
			cache = new byte[all.size()][WGROUP.all().size()];
			cacheVer = version;
			cacheAt = now;
		}
		byte c = cache[e.index()][g.index];
		if (c == 0)
			cache[e.index()][g.index] = c = (byte) (blocked(e, g) ? 2 : 1);
		return c == 2 ? 0 : vanilla;
	}

	private static boolean blocked(RoomEmployment e, WGROUP g) {
		HGROUP hg = HGROUP.get(g.type.CLASS, g.race);
		if (hg == null)
			return false;
		RoomBlueprintIns<?> b = e.blueprint();
		boolean any = false;
		for (int i = 0; i < b.instancesSize(); i++) {
			RoomInstance ins = b.getInstance(i);
			if (!ins.active())
				continue;
			any = true;
			Entry en = MAP.get(ins);
			if (en == null || allows(en.w, hg))
				return false;
		}
		return any;
	}
}
