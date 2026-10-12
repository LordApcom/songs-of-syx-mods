package lordapcom.cdream;

import java.util.Arrays;

import settlement.entry.EntryPoints.EntryPoint;
import settlement.main.SETT;
import settlement.path.path.SPath;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.Coo;

/**
 * State of a sea-locked ("island") capital: no land entry is reachable, but the throne's land touches sea that
 * connects to the map edge. The throne's player-walled-in case is NOT this: it keeps the vanilla Capital Closed warning.
 */
final class Island {

	private static final int WARMUP_SECONDS = 3;
	private static final int DOCK_REFRESH_SECONDS = 5;

	/** Saved: the capital started (or was found) isolated by sea. */
	static boolean island;
	/** Transient: island and currently no land entry, with at least one dock. Read by the mixins. */
	static volatile boolean locked;

	private static boolean evaluated;
	private static double timer;
	private static int seconds;
	private static int[] docks = new int[0];
	private static final Coo tmp = new Coo();

	private Island() {
	}

	static void reset() {
		Boats.reset();
		island = false;
		locked = false;
		evaluated = false;
		timer = 0;
		seconds = 0;
		docks = new int[0];
	}

	static void restore(boolean wasIsland) {
		island = wasIsland;
		evaluated = wasIsland; // a save taken before the first check (e.g. right after landing) is checked again
	}

	static void update(double ds) {
		if (!SETT.exists())
			return;
		timer += ds;
		if (timer < 1)
			return;
		timer = 0;
		seconds++;

		boolean landEntry = landEntryReachable();
		if (!evaluated) {
			if (seconds < WARMUP_SECONDS)
				return; // let the path components settle before judging
			evaluated = true;
			if (!landEntry) {
				scanDocks();
				island = docks.length > 0;
			}
		}
		if (!island || landEntry) {
			setLocked(false);
			return;
		}
		if (seconds % DOCK_REFRESH_SECONDS == 0 || docks.length == 0)
			scanDocks();
		setLocked(docks.length > 0);
	}

	private static void setLocked(boolean v) {
		if (v != locked) {
			locked = v;
			SETT.ENTRY().points.updateAvailability(); // makes the game recompute "reachable" next tick
		}
	}

	private static boolean landEntryReachable() {
		for (EntryPoint p : Lists.of(SETT.ENTRY().points.active()))
			if (SETT.PATH().connectivity.is(p.coo().x(), p.coo().y()))
				return true;
		return false;
	}

	/** Sea connected to the map edge; for every sea tile, the next step toward the edge. */
	static boolean[] sea = new boolean[0];
	static int[] toEdge = new int[0];

	/** Land tiles of the throne's area that touch sea connected to the map edge. */
	private static void scanDocks() {
		final int w = SETT.TWIDTH, h = SETT.THEIGHT;
		if (sea.length != w * h) {
			sea = new boolean[w * h];
			toEdge = new int[w * h];
		} else
			Arrays.fill(sea, false);
		int[] queue = new int[w * h];
		int qt = 0;
		for (int i = 0; i < w; i++) {
			qt = seed(i, 0, w, -1, sea, toEdge, queue, qt);
			qt = seed(i, h - 1, w, -1, sea, toEdge, queue, qt);
		}
		for (int i = 0; i < h; i++) {
			qt = seed(0, i, w, -1, sea, toEdge, queue, qt);
			qt = seed(w - 1, i, w, -1, sea, toEdge, queue, qt);
		}
		qt = flood(queue, 0, qt, w, sea, toEdge);
		int seaCount = qt;

		int[] out = new int[256];
		int n = 0;
		boolean[] done = new boolean[w * h];
		for (int i = 0; i < seaCount; i++) {
			int x = queue[i] % w, y = queue[i] / w;
			for (int d = 0; d < 4; d++) {
				int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
				if (!SETT.IN_BOUNDS(nx, ny) || sea[nx + ny * w] || done[nx + ny * w])
					continue;
				if (SETT.PATH().connectivity.is(nx, ny)) {
					done[nx + ny * w] = true;
					if (n == out.length)
						out = Arrays.copyOf(out, n * 2);
					out[n++] = nx + ny * w;
				}
			}
		}
		docks = Arrays.copyOf(out, n);
	}

	/** 8-way flood over sea tiles; a diagonal step needs both side tiles to be sea, like the game's boats. */
	private static int flood(int[] queue, int qh, int qt, int w, boolean[] mark, int[] step) {
		final int[] DX = { 1, -1, 0, 0, 1, 1, -1, -1 }, DY = { 0, 0, 1, -1, 1, -1, 1, -1 };
		while (qh < qt) {
			int i = queue[qh++];
			int x = i % w, y = i / w;
			for (int d = 0; d < 8; d++) {
				int nx = x + DX[d], ny = y + DY[d];
				if (!SETT.IN_BOUNDS(nx, ny) || !isWater(nx, ny))
					continue;
				int ni = nx + ny * w;
				if (mark[ni])
					continue;
				if (d >= 4 && (!isWater(x + DX[d], y) || !isWater(x, y + DY[d])))
					continue;
				mark[ni] = true;
				step[ni] = i;
				queue[qt++] = ni;
			}
		}
		return qt;
	}

	private static int seed(int x, int y, int w, int parent, boolean[] mark, int[] step, int[] queue, int qt) {
		if (!SETT.IN_BOUNDS(x, y) || mark[x + y * w] || !isWater(x, y))
			return qt;
		mark[x + y * w] = true;
		step[x + y * w] = parent;
		queue[qt] = x + y * w;
		return qt + 1;
	}

	private static boolean isWater(int x, int y) {
		return SETT.TERRAIN().WATER.is.is(x, y);
	}

	static boolean isSea(int x, int y) {
		int i = x + y * SETT.TWIDTH;
		return SETT.IN_BOUNDS(x, y) && i < sea.length && sea[i];
	}

	/** The sea tile (connected to the map edge) nearest (x,y) within radius, or -1. */
	static int nearestSea(int x, int y, int radius) {
		int best = -1, bestD = Integer.MAX_VALUE;
		for (int dy = -radius; dy <= radius; dy++)
			for (int dx = -radius; dx <= radius; dx++) {
				int d = dx * dx + dy * dy;
				if (d < bestD && isSea(x + dx, y + dy)) {
					bestD = d;
					best = x + dx + (y + dy) * SETT.TWIDTH;
				}
			}
		return best;
	}

	/** Sea route from the map edge to the sea tile nearest (x,y), or null. */
	static int[] routeFromEdge(int x, int y, int radius) {
		int best = nearestSea(x, y, radius);
		if (best < 0)
			return null;
		int[] p = chain(best, toEdge);
		for (int i = 0, j = p.length - 1; i < j; i++, j--) {
			int t = p[i];
			p[i] = p[j];
			p[j] = t;
		}
		return p;
	}

	/** The half of the docks farthest from (x,y), farthest first. */
	static int[] farDocks(int x, int y) {
		Integer[] d = new Integer[docks.length];
		for (int i = 0; i < d.length; i++)
			d[i] = docks[i];
		java.util.Arrays.sort(d, (a, b) -> Long.compare(dist(b, x, y), dist(a, x, y)));
		int[] r = new int[(d.length + 1) / 2];
		for (int i = 0; i < r.length; i++)
			r[i] = d[i];
		return r;
	}

	private static long dist(int tile, int x, int y) {
		long dx = tile % SETT.TWIDTH - x, dy = tile / SETT.TWIDTH - y;
		return dx * dx + dy * dy;
	}

	private static int[] chain(int from, int[] step) {
		int n = 1;
		for (int i = step[from]; i >= 0 && n <= sea.length; i = step[i])
			n++;
		int[] p = new int[Math.max(n, 2)];
		p[0] = from;
		int k = 1;
		for (int i = step[from]; i >= 0 && k < n; i = step[i])
			p[k++] = i;
		if (n == 1)
			p[1] = from;
		return p;
	}

	/** True when a leaver standing at (x,y) belongs to the island's land and must use a dock. */
	static boolean isDockSide(int x, int y) {
		return locked && docks.length > 0 && SETT.PATH().connectivity.is(x, y);
	}

	static COORDINATE randomDock(int rr) {
		if (docks.length == 0)
			return null;
		int d = docks[(rr & 0x7fffffff) % docks.length];
		tmp.set(d % SETT.TWIDTH, d / SETT.TWIDTH);
		return tmp;
	}

	static boolean pathToDock(int sx, int sy, SPath path) {
		int best = -1;
		long bestD = Long.MAX_VALUE;
		for (int d : docks) {
			long dx = d % SETT.TWIDTH - sx, dy = d / SETT.TWIDTH - sy;
			long dist = dx * dx + dy * dy;
			if (dist < bestD) {
				bestD = dist;
				best = d;
			}
		}
		if (best < 0)
			return false;
		tmp.set(best % SETT.TWIDTH, best / SETT.TWIDTH);
		return path.request(sx, sy, tmp);
	}
}
