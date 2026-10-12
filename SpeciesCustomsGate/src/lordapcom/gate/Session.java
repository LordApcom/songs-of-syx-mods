package lordapcom.gate;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;

import game.GAME;
import init.type.HGROUP;
import init.type.HTYPE;
import init.type.HTYPES;
import settlement.entity.EPHYSICS;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.path.AVAILABILITY;
import settlement.room.infra.gate.ROOM_GATE;
import settlement.room.main.Room;
import settlement.room.main.furnisher.FurnisherItem;
import settlement.stats.STATS;
import snake2d.LOG;
import vizardalpha.polyphon.api.InstanceState;

/**
 * Everything the mod knows about the current game: the gate segments, their filters, and the cached
 * reachability maps. One Session per settlement (keyed on the settlement's PATHING object through
 * Polyphon's InstanceState), so nothing survives into the next game.
 */
final class Session {

	enum Reach {
		CONNECTED, DISCONNECTED, BUDGET_EXHAUSTED
	}

	/** Persisted part of a gate, keyed by its anchor tile. */
	static final class Entry {
		final int[] bits;
		final boolean inv;

		Entry(int[] bits, boolean inv) {
			this.bits = bits;
			this.inv = inv;
		}
	}

	/** A contiguous run of gate tiles. Passage runs along the longer side; the arrow follows that axis. */
	static final class Gate {
		final int id, anchor;
		final int[] tiles;
		int x1, y1, x2, y2;
		boolean axisY;
		int[] bits; // null = everyone allowed
		boolean inv;

		Gate(int id, int anchor, int[] tiles) {
			this.id = id;
			this.anchor = anchor;
			this.tiles = tiles;
		}

		int dir() {
			return inv ? -1 : 1;
		}

		int lo() {
			return axisY ? y1 : x1;
		}

		int hi() {
			return axisY ? y2 : x2;
		}

		/** Axis coordinate of the first gate layer an arrow-following walker meets. */
		int entry() {
			return dir() > 0 ? lo() : hi();
		}

		boolean allows(HGROUP g) {
			return bits == null || g == null || bit(bits, g.index);
		}

		int allowedCount() {
			int n = 0;
			for (HGROUP g : Lists.of(HGROUP.all()))
				if (allows(g))
					n++;
			return n;
		}
	}

	/** Reachability with the gates restricted for one species group treated as one-way valves. */
	private static final class Lab {
		int[] lbl;
		int[] ef, et; // directed region edges (from, to)
		int layout, avail, at;
	}

	/** Per-humanoid safety-net state. Absent for almost everyone. Dies with the humanoid. */
	static final class HState {
		int bumps, bumpAt;
		int rescues, rescueAt;
		int avoidX = -1, avoidY = -1, avoidUntil;
	}

	static final InstanceState<Session> SES = InstanceState.withInitial(Session::new);
	static final InstanceState<HState> H = InstanceState.of();
	/** Humanoid whose AI is choosing a plan right now (set by PlanMixin, cleared every tick as a leak guard). */
	static final ThreadLocal<Humanoid> CUR = new ThreadLocal<>();

	private static final int REPAIR_AGE = 60; // ticks before availability changes are re-flooded
	private static final int FLOODS_PER_TICK = 2;
	private static final int BUMPS_TO_RESCUE = 8, BUMP_WINDOW = 240;
	private static final int AVOID_TICKS = 1800, RESCUES_TO_UNEMPLOY = 3, RESCUE_WINDOW = 7200;

	private static int errs;

	static Session get() {
		try {
			return SES.get(SETT.PATH());
		} catch (Throwable t) {
			return null;
		}
	}

	static void err(String where, Throwable t) {
		if (errs++ < 5)
			LOG.err("[SCG] " + where + ": " + t);
	}

	// ---- bit helpers ----

	static boolean bit(int[] b, int i) {
		return (b[i >>> 5] & (1 << (i & 31))) != 0;
	}

	static int words() {
		return (HGROUP.all().size() + 31) >>> 5;
	}

	/** Null when everyone is allowed. */
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

	// ---- state ----

	final HashMap<Integer, Entry> store = new HashMap<>();
	volatile Gate[] gates = new Gate[0];
	volatile short[] segAt;
	/** True when at least one gate restricts someone. All hooks return immediately otherwise. */
	volatile boolean active;
	volatile boolean dirty = true;
	private int scanAt = -1_000_000;
	private int layoutGen;
	private int layoutSig;
	private int floods;
	private BitSet[] sigs;
	private int sigGen = -1;
	private final int[] s0 = new int[4], t0 = new int[4], s1 = new int[4], t1 = new int[4]; // single-threaded scratch
	private int[] queue;
	private int[] l0;
	private int l0Avail, l0At;
	private final HashMap<BitSet, Lab> labs = new HashMap<>();
	private final java.util.ArrayList<Humanoid> rescue = new java.util.ArrayList<>();

	private static int now() {
		return GAME.updateI();
	}

	/** Once per game tick, from the script instance. */
	void tick() {
		floods = 0;
		int t = now();
		if (dirty && t - scanAt >= 30 || t - scanAt >= 300 || t < scanAt)
			scan();
		if (!rescue.isEmpty())
			rescueAll();
	}

	// ---- gate discovery ----

	void scan() {
		dirty = false;
		scanAt = now();
		int n = SETT.TAREA, w = SETT.TWIDTH;
		short[] seg = new short[n];
		if (queue == null || queue.length < n)
			queue = new int[n];
		java.util.ArrayList<Gate> list = new java.util.ArrayList<>();
		for (int s = 0; s < n; s++) {
			if (seg[s] != 0 || !isGateTile(s))
				continue;
			int id = list.size();
			if (id >= Short.MAX_VALUE)
				break;
			int head = 0, tail = 0;
			queue[tail++] = s;
			seg[s] = (short) (id + 1);
			int x1 = w, x2 = -1, y1 = w, y2 = -1;
			while (head < tail) {
				int t = queue[head++];
				int x = t % w, y = t / w;
				x1 = Math.min(x1, x);
				x2 = Math.max(x2, x);
				y1 = Math.min(y1, y);
				y2 = Math.max(y2, y);
				if (x > 0)
					tail = push(seg, t - 1, id, tail);
				if (x < w - 1)
					tail = push(seg, t + 1, id, tail);
				if (y > 0)
					tail = push(seg, t - w, id, tail);
				if (y < w - 1)
					tail = push(seg, t + w, id, tail);
			}
			Gate g = new Gate(id, s, Arrays.copyOf(queue, tail));
			g.x1 = x1;
			g.x2 = x2;
			g.y1 = y1;
			g.y2 = y2;
			// The gate sits IN the wall, so people cross it perpendicular to the wall line. Find the wall line from
			// the solid tiles just beyond the gate's ends (seen in game: a 1x3 gatehouse in a north-south wall is
			// crossed west-east); fall back to the gate's short side.
			int gw = x2 - x1 + 1, gh = y2 - y1 + 1;
			int wallsEW = (solidAt(x1 - 1, (y1 + y2) / 2) ? 1 : 0) + (solidAt(x2 + 1, (y1 + y2) / 2) ? 1 : 0);
			int wallsNS = (solidAt((x1 + x2) / 2, y1 - 1) ? 1 : 0) + (solidAt((x1 + x2) / 2, y2 + 1) ? 1 : 0);
			if (wallsEW != wallsNS)
				g.axisY = wallsEW > wallsNS; // wall runs east-west -> cross north-south
			else if (gw != gh)
				g.axisY = gw > gh;           // the long side lies along the wall
			else {
				FurnisherItem it = SETT.ROOMS().fData.item.get(s % w, s / w);
				g.axisY = it != null && it.width() > it.height();
			}
			list.add(g);
		}

		// restore filters by anchor; a gate that grew or merged inherits any old anchor inside it
		HashMap<Integer, Entry> old = new HashMap<>(store);
		store.clear();
		int sig = 17;
		boolean any = false;
		for (Gate g : list) {
			Entry e = old.get(g.anchor);
			if (e == null)
				for (java.util.Map.Entry<Integer, Entry> o : old.entrySet())
					if (seg[o.getKey()] == g.id + 1) {
						e = o.getValue();
						break;
					}
			if (e != null) {
				g.bits = e.bits;
				g.inv = e.inv;
				if (e.bits != null || e.inv)
					store.put(g.anchor, e);
			}
			any |= g.bits != null;
			sig = sig * 31 + g.anchor;
			sig = sig * 31 + g.tiles.length;
			sig = sig * 31 + (g.axisY ? 1 : 0) + (g.inv ? 2 : 0) + Arrays.hashCode(g.bits) * 7;
		}
		if (sig != layoutSig) {
			layoutSig = sig;
			layoutGen++;
		}
		segAt = seg;
		gates = list.toArray(new Gate[0]);
		active = any;
	}

	private int push(short[] seg, int t, int id, int tail) {
		if (seg[t] == 0 && isGateTile(t)) {
			seg[t] = (short) (id + 1);
			queue[tail++] = t;
		}
		return tail;
	}

	private static boolean isGateTile(int tile) {
		Room r = SETT.ROOMS().map.get(tile);
		return r != null && r.blueprint() instanceof ROOM_GATE;
	}

	private static boolean solidAt(int x, int y) {
		return SETT.IN_BOUNDS(x, y) && SETT.PATH().solidity.is(x, y);
	}

	Gate gateAt(int tx, int ty) {
		short[] seg = segAt;
		if (seg == null || !SETT.IN_BOUNDS(tx, ty))
			return null;
		int i = seg[tx + ty * SETT.TWIDTH] - 1;
		Gate[] g = gates;
		return i >= 0 && i < g.length ? g[i] : null;
	}

	private int segOf(int tx, int ty) {
		short[] seg = segAt;
		return seg == null || !SETT.IN_BOUNDS(tx, ty) ? 0 : seg[tx + ty * SETT.TWIDTH];
	}

	void edit(Gate g, int[] bits, boolean inv) {
		g.bits = bits;
		g.inv = inv;
		if (bits == null && !inv)
			store.remove(g.anchor);
		else
			store.put(g.anchor, new Entry(bits, inv));
		boolean any = false;
		for (Gate o : gates)
			any |= o.bits != null;
		active = any;
		layoutGen++;
		dirty = true;
	}

	// ---- who is subject to the filter ----

	/** The species group the filter applies to, or null when this humanoid always passes. */
	static HGROUP subject(Humanoid h) {
		HTYPE t = h.indu().hType();
		if (h.indu().hostile() || t == HTYPES.TOURIST() || t == HTYPES.SOLDIER())
			return null;
		return HGROUP.get(h);
	}

	// ---- physical rule (used by CollisionMixin) ----

	/** True when the gate tile (tx,ty) must act as a wall for this humanoid right now. */
	boolean blocks(Humanoid h, int tx, int ty) {
		Gate g = gateAt(tx, ty);
		if (g == null || g.bits == null)
			return false;
		HGROUP grp = subject(h);
		if (grp == null || g.allows(grp))
			return false;
		EPHYSICS.Solid p = h.physics;
		boolean adjacent = false;
		for (int y = p.ty1(); y <= p.ty2(); y++)
			for (int x = p.tx1(); x <= p.tx2(); x++) {
				if (segOf(x, y) == g.id + 1)
					return false; // already inside the corridor: movement there is free
				adjacent |= Math.abs(x - tx) <= 1 && Math.abs(y - ty) <= 1;
			}
		if (!adjacent)
			return false; // spawned or teleported inside: never trap, never get killed by the resolver
		if ((g.axisY ? ty : tx) != g.entry())
			return true; // not the entrance layer: against the arrow, or through the side
		int out = g.entry() - g.dir();
		for (int y = p.ty1(); y <= p.ty2(); y++)
			for (int x = p.tx1(); x <= p.tx2(); x++)
				if (((g.axisY ? y : x) - out) * g.dir() > 0)
					return true;
		return false; // straight in from the arrow's back side
	}

	// ---- reachability ----

	private static boolean passable(int tile) {
		AVAILABILITY a = SETT.PATH().availability.get(tile);
		return a != null && a.player >= 0;
	}

	private int[] flood(BitSet blocked) {
		int n = SETT.TAREA, w = SETT.TWIDTH;
		short[] seg = segAt;
		int[] lbl = new int[n];
		Arrays.fill(lbl, -1);
		int[] q = queue;
		int next = 0;
		for (int s = 0; s < n; s++) {
			if (lbl[s] != -1 || !open(s, seg, blocked))
				continue;
			int head = 0, tail = 0;
			q[tail++] = s;
			lbl[s] = next;
			while (head < tail) {
				int t = q[head++];
				int x = t % w, y = t / w;
				int u;
				if (x > 0 && lbl[u = t - 1] == -1 && open(u, seg, blocked)) {
					lbl[u] = next;
					q[tail++] = u;
				}
				if (x < w - 1 && lbl[u = t + 1] == -1 && open(u, seg, blocked)) {
					lbl[u] = next;
					q[tail++] = u;
				}
				if (y > 0 && lbl[u = t - w] == -1 && open(u, seg, blocked)) {
					lbl[u] = next;
					q[tail++] = u;
				}
				if (y < w - 1 && lbl[u = t + w] == -1 && open(u, seg, blocked)) {
					lbl[u] = next;
					q[tail++] = u;
				}
			}
			next++;
		}
		return lbl;
	}

	private static boolean open(int tile, short[] seg, BitSet blocked) {
		if (!passable(tile))
			return false;
		return blocked == null || seg == null || seg[tile] == 0 || !blocked.get(seg[tile] - 1);
	}

	private int tileOf(Gate g, int c, int p) {
		int w = SETT.TWIDTH;
		if (c < 0 || c >= w || p < 0 || p >= w)
			return -1;
		return g.axisY ? p + c * w : c + p * w;
	}

	/** Null when the per-tick flood budget is spent and no earlier result exists. */
	private int[] base() {
		int av = SETT.PATH().availability.state();
		boolean stale = l0 != null && l0Avail != av && now() - l0At >= REPAIR_AGE;
		if (l0 == null || stale) {
			if (floods >= FLOODS_PER_TICK)
				return l0;
			floods++;
			l0 = flood(null);
			l0Avail = av;
			l0At = now();
		}
		return l0;
	}

	private Lab lab(BitSet sig) {
		Lab lab = labs.get(sig);
		int av = SETT.PATH().availability.state();
		boolean stale = lab != null && (lab.layout != layoutGen || lab.avail != av && now() - lab.at >= REPAIR_AGE);
		if (lab == null || stale) {
			if (floods >= FLOODS_PER_TICK)
				return lab != null && lab.layout == layoutGen ? lab : null;
			floods++;
			if (labs.size() > 16)
				labs.clear();
			lab = build(sig);
			labs.put(sig, lab);
		}
		return lab;
	}

	private Lab build(BitSet sig) {
		Lab lab = new Lab();
		lab.layout = layoutGen;
		lab.avail = SETT.PATH().availability.state();
		lab.at = now();
		lab.lbl = flood(sig);
		java.util.ArrayList<int[]> edges = new java.util.ArrayList<>();
		Gate[] gs = gates;
		for (int i = sig.nextSetBit(0); i >= 0; i = sig.nextSetBit(i + 1)) {
			if (i >= gs.length)
				break;
			Gate g = gs[i];
			int pLo = g.axisY ? g.x1 : g.y1, pHi = g.axisY ? g.x2 : g.y2;
			for (int p = pLo; p <= pHi; p++) {
				boolean lane = true;
				for (int c = g.lo(); c <= g.hi() && lane; c++) {
					int t = tileOf(g, c, p);
					lane = t >= 0 && segAt[t] == g.id + 1 && passable(t);
				}
				if (!lane)
					continue;
				int a = tileOf(g, g.dir() > 0 ? g.lo() - 1 : g.hi() + 1, p);
				int b = tileOf(g, g.dir() > 0 ? g.hi() + 1 : g.lo() - 1, p);
				if (a < 0 || b < 0)
					continue;
				int la = lab.lbl[a], lb = lab.lbl[b];
				if (la >= 0 && lb >= 0 && la != lb)
					edges.add(new int[] { la, lb });
			}
		}
		lab.ef = new int[edges.size()];
		lab.et = new int[edges.size()];
		for (int i = 0; i < lab.ef.length; i++) {
			lab.ef[i] = edges.get(i)[0];
			lab.et[i] = edges.get(i)[1];
		}
		return lab;
	}

	/** The tile's own region, or the regions of its walkable neighbours when the tile itself is solid. */
	private static int around(int[] lbl, int tx, int ty, int[] out) {
		int w = SETT.TWIDTH, n = 0;
		if (!SETT.IN_BOUNDS(tx, ty))
			return 0;
		int own = lbl[tx + ty * w];
		if (own >= 0) {
			out[0] = own;
			return 1;
		}
		int[][] d = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		for (int[] o : d)
			if (SETT.IN_BOUNDS(tx + o[0], ty + o[1])) {
				int l = lbl[tx + o[0] + (ty + o[1]) * w];
				if (l >= 0)
					out[n++] = l;
			}
		return n;
	}

	private static boolean common(int[] a, int na, int[] b, int nb) {
		for (int i = 0; i < na; i++)
			for (int j = 0; j < nb; j++)
				if (a[i] == b[j])
					return true;
		return false;
	}

	private static boolean flows(Lab lab, int[] from, int nf, int[] to, int nt) {
		if (common(from, nf, to, nt))
			return true;
		HashSet<Integer> seen = new HashSet<>();
		for (int i = 0; i < nf; i++)
			seen.add(from[i]);
		boolean grew = true;
		while (grew) {
			for (int i = 0; i < nt; i++)
				if (seen.contains(to[i]))
					return true;
			grew = false;
			for (int i = 0; i < lab.ef.length; i++)
				if (seen.contains(lab.ef[i]) && seen.add(lab.et[i]))
					grew = true;
		}
		return false;
	}

	/** Which gates reject this species group. Cached per layout; never mutated after creation. */
	private BitSet signature(HGROUP g) {
		if (sigGen != layoutGen || sigs == null) {
			sigs = new BitSet[HGROUP.all().size()];
			sigGen = layoutGen;
		}
		BitSet b = sigs[g.index];
		if (b == null) {
			b = new BitSet();
			for (Gate gt : gates)
				if (!gt.allows(g))
					b.set(gt.id);
			sigs[g.index] = b;
		}
		return b;
	}

	/**
	 * Can this humanoid get to the tile through the gate rules? mutual = it must also be able to come back
	 * (leisure targets), otherwise one-way is fine (workplaces). DISCONNECTED is only returned when the tile is
	 * reachable without gates but not with them.
	 */
	Reach reach(Humanoid h, int tx, int ty, boolean mutual) {
		if (!active)
			return Reach.CONNECTED;
		HGROUP g = subject(h);
		if (g == null)
			return Reach.CONNECTED;
		BitSet sig = signature(g);
		if (sig.isEmpty())
			return Reach.CONNECTED;
		int sx = h.tc().x(), sy = h.tc().y();
		int a = segOf(sx, sy), b = segOf(tx, ty);
		if (a > 0 && sig.get(a - 1) || b > 0 && sig.get(b - 1))
			return Reach.CONNECTED;
		int[] base = base();
		Lab lab = base == null ? null : lab(sig);
		if (lab == null)
			return Reach.BUDGET_EXHAUSTED;
		int ns0 = around(base, sx, sy, s0), nt0 = around(base, tx, ty, t0);
		if (ns0 == 0 || nt0 == 0 || !common(s0, ns0, t0, nt0))
			return Reach.CONNECTED; // unreachable even without gates: not the gates' business
		int ns = around(lab.lbl, sx, sy, s1), nt = around(lab.lbl, tx, ty, t1);
		if (ns == 0 || nt == 0)
			return Reach.CONNECTED;
		if (!flows(lab, s1, ns, t1, nt))
			return Reach.DISCONNECTED;
		if (mutual && !flows(lab, t1, nt, s1, ns))
			return Reach.DISCONNECTED;
		return Reach.CONNECTED;
	}

	// ---- stuck safety net ----

	/** A forbidden humanoid hit a gate tile (from Humanoid.collideTile). */
	void bump(Humanoid h) {
		HState st = H.get(h);
		if (st == null)
			H.set(h, st = new HState());
		int t = now();
		if (t - st.bumpAt > BUMP_WINDOW || t < st.bumpAt)
			st.bumps = 0;
		st.bumpAt = t;
		if (++st.bumps >= BUMPS_TO_RESCUE) {
			st.bumps = 0;
			if (!rescue.contains(h))
				rescue.add(h);
		}
	}

	/** Drops the current plan, remembers the target so service searches skip it, and frees repeat offenders from their job. */
	private void rescueAll() {
		java.util.ArrayList<Humanoid> l = new java.util.ArrayList<>(rescue);
		rescue.clear();
		int t = now();
		for (Humanoid h : l) {
			try {
				if (h.isRemoved())
					continue;
				HState st = H.get(h);
				if (st == null)
					continue;
				snake2d.util.datatypes.COORDINATE d = h.ai().getDestination();
				if (d != null) {
					st.avoidX = d.x();
					st.avoidY = d.y();
					st.avoidUntil = t + AVOID_TICKS;
				}
				if (t - st.rescueAt > RESCUE_WINDOW || t < st.rescueAt)
					st.rescues = 0;
				st.rescueAt = t;
				if (++st.rescues >= RESCUES_TO_UNEMPLOY) {
					st.rescues = 0;
					if (STATS.WORK().EMPLOYED.get(h) != null)
						STATS.WORK().EMPLOYED.set(h, null);
				}
				h.interrupt();
			} catch (Throwable e) {
				err("rescue", e);
			}
		}
	}

	static boolean avoids(Humanoid h, int tx, int ty) {
		HState st = H.get(h);
		return st != null && st.avoidX == tx && st.avoidY == ty && now() < st.avoidUntil;
	}

	// ---- text ----

	String summary(Gate g) {
		if (g.bits == null)
			return "Everyone";
		int n = g.allowedCount(), all = HGROUP.all().size();
		if (n == 0)
			return "NONE allowed";
		StringBuilder sb = new StringBuilder("Allowed (" + n + "/" + all + "): ");
		int shown = 0;
		for (HGROUP s : Lists.of(HGROUP.all()))
			if (g.allows(s)) {
				if (shown == 6) {
					sb.append("...");
					break;
				}
				sb.append(shown++ == 0 ? "" : ", ").append(s.name);
			}
		return sb.toString();
	}

	static String arrowName(Gate g) {
		return g.axisY ? (g.dir() > 0 ? "down" : "up") : (g.dir() > 0 ? "right" : "left");
	}
}
