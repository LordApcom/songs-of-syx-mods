package lordapcom.wsf;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

	// ---- job allocation (see EmployerMixin, PrioMixin) ----

	/** The game's job allocator while it runs (Employer.update), else null: the priority screen sees real values. */
	static Object allocator;
	private static LIST<RoomEmployment> ownersFor;
	private static final IdentityHashMap<Object, RoomEmployment> owners = new IdentityHashMap<>();
	private static Method wGet, wAdd;

	/**
	 * The allocator hands out job slots per workplace type and species, not per building. In a type where some
	 * active building refuses someone, a species gets priority 0 once the buildings that accept it are full with the
	 * slots already handed out, so the rest go to the next species in line instead of waiting for people the
	 * buildings refuse.
	 */
	static int effectivePrio(Object prio, int vanilla, WGROUP g) throws Exception {
		Object al = allocator;
		if (al == null || vanilla == 0 || g == null || !anyW)
			return vanilla;
		RoomEmployment e = ownerOf(prio);
		if (e == null)
			return vanilla;
		Object[] buckets = vizardalpha.polyphon.api.Shadow.get(al, "buckets");
		int[] left = place(e, buckets[e.index()]);
		return left == null || left[WGROUP.all().size() + g.index] > 0 ? vanilla : 0;
	}

	/** After each allocator step: give back slots no building of the type can seat (see place). */
	static void trim(Object al) throws Exception {
		if (!anyW)
			return;
		Object[] buckets = vizardalpha.polyphon.api.Shadow.get(al, "buckets");
		Object available = vizardalpha.polyphon.api.Shadow.get(al, "available");
		LIST<RoomEmployment> all = SETT.ROOMS().employment.ALL();
		int n = WGROUP.all().size();
		for (int i = 0; i < buckets.length && i < all.size(); i++) {
			int[] r = place(all.get(i), buckets[i]);
			if (r == null)
				continue;
			for (int gi = 0; gi < n; gi++) {
				if (r[gi] > 0) {
					WGROUP g = WGROUP.all().get(gi);
					wAdd.invoke(buckets[i], g, -r[gi]);
					wAdd.invoke(available, g, r[gi]);
				}
			}
		}
	}

	/**
	 * Seats the slots each species holds in this type into the active buildings that accept it, most restricted
	 * species first. Returns null when no active building of the type has a worker filter; else, per group, the slots
	 * that found no seat ([0, n)) and the free seats left in buildings that accept it ([n, 2n)).
	 * ponytail: greedy, exact when the accepted sets nest (the usual case); a max-flow would be exact always.
	 */
	private static int[] place(RoomEmployment e, Object bucket) throws Exception {
		RoomBlueprintIns<?> b = e.blueprint();
		if (!typeFiltered(b))
			return null;
		int m = 0;
		for (int i = 0; i < b.instancesSize(); i++)
			if (b.getInstance(i).active())
				m++;
		int n = WGROUP.all().size();
		int[] seats = new int[m];
		boolean[][] ok = new boolean[n][m];
		int[] accepts = new int[n];
		int k = 0;
		for (int i = 0; i < b.instancesSize(); i++) {
			RoomInstance ins = b.getInstance(i);
			if (!ins.active())
				continue;
			seats[k] = ins.employees().target();
			Entry en = MAP.get(ins);
			for (int gi = 0; gi < n; gi++) {
				WGROUP g = WGROUP.all().get(gi);
				HGROUP hg = HGROUP.get(g.type.CLASS, g.race);
				if (en == null || allows(en.w, hg)) {
					ok[gi][k] = true;
					accepts[gi]++;
				}
			}
			k++;
		}
		Integer[] order = new Integer[n];
		for (int gi = 0; gi < n; gi++)
			order[gi] = gi;
		Arrays.sort(order, (x, y) -> accepts[x] - accepts[y]);
		int[] r = new int[2 * n];
		for (int gi : order) {
			int have = held(bucket, WGROUP.all().get(gi));
			for (int i = 0; i < m && have > 0; i++) {
				if (ok[gi][i]) {
					int t = Math.min(have, seats[i]);
					seats[i] -= t;
					have -= t;
				}
			}
			r[gi] = have;
		}
		for (int gi = 0; gi < n; gi++)
			for (int i = 0; i < m; i++)
				if (ok[gi][i])
					r[n + gi] += seats[i];
		return r;
	}

	/** True when an active building of this workplace type has a worker filter. */
	static boolean typeFiltered(RoomInstance ins) {
		return typeFiltered(ins.blueprintI());
	}

	private static boolean typeFiltered(RoomBlueprintIns<?> b) {
		for (int i = 0; i < b.instancesSize(); i++) {
			RoomInstance ins = b.getInstance(i);
			Entry en = MAP.get(ins);
			if (ins.active() && en != null && en.w != null)
				return true;
		}
		return false;
	}

	private static int held(Object workers, WGROUP g) throws Exception {
		if (wGet == null) {
			Class<?> w = Class.forName("settlement.room.main.employment.Employer$Workers");
			Method get = w.getDeclaredMethod("get", WGROUP.class), add = w.getDeclaredMethod("add", WGROUP.class, int.class);
			get.setAccessible(true);
			add.setAccessible(true);
			wAdd = add;
			wGet = get;
		}
		return (Integer) wGet.invoke(workers, g);
	}

	private static RoomEmployment ownerOf(Object prio) {
		LIST<RoomEmployment> all = SETT.ROOMS().employment.ALL();
		if (all != ownersFor) {
			ownersFor = all;
			owners.clear();
			for (RoomEmployment e : Lists.of(all))
				owners.put(e.priorities, e);
		}
		return owners.get(prio);
	}
}
