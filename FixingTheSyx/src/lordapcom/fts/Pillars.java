package lordapcom.fts;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

import settlement.main.SETT;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.placement.RoomPlacer;
import view.tool.PLACER_TYPE;
import view.tool.PlacableMulti;
import vizardalpha.polyphon.api.Shadow;

/**
 * Feature 5: support pillar autofill. In vanilla a pillar is a hole in the room area (placed with the
 * shrink tool), and a tile is "yellow" while too little of its surroundings is non-room. The plan is a
 * symmetric grid centred on the room, consumed one cluster at a time (about 0.5 s apart) so the yellow
 * map can refresh. Symmetry is preferred over completeness: a few yellow tiles may remain.
 */
final class Pillars {

	private static final int GRID = 8;
	private static final long DELAY_NS = 500_000_000L;
	private static final int[] DX = {0, 1, -1, 0, 0};
	private static final int[] DY = {0, 0, 0, 1, -1};

	private static final ArrayDeque<int[][]> queue = new ArrayDeque<>();
	private static RoomBlueprintImp blueprint;
	private static long last;

	private Pillars() {
	}

	private static boolean embryo(int x, int y) {
		return SETT.IN_BOUNDS(x, y) && SETT.ROOMS().placement.embryo.is(x, y);
	}

	static boolean start(RoomPlacer rp, RoomBlueprintImp b, int tx, int ty) {
		refresh(rp);
		if (!embryo(tx, ty) || !rp.cost().is(tx, ty))
			return false; // not a yellow tile: let the click behave as usual
		// connected area of the room being planned
		int[] bb = {tx, ty, tx, ty};
		ArrayDeque<int[]> q = new ArrayDeque<>();
		ArrayList<int[]> tiles = new ArrayList<>();
		HashSet<Long> done = new HashSet<>();
		q.add(new int[] {tx, ty});
		done.add(key(tx, ty));
		while (!q.isEmpty()) {
			int[] c = q.poll();
			tiles.add(c);
			bb[0] = Math.min(bb[0], c[0]);
			bb[1] = Math.min(bb[1], c[1]);
			bb[2] = Math.max(bb[2], c[0]);
			bb[3] = Math.max(bb[3], c[1]);
			for (int i = 1; i < 5; i++) {
				int x = c[0] + DX[i], y = c[1] + DY[i];
				if (embryo(x, y) && done.add(key(x, y)))
					q.add(new int[] {x, y});
			}
		}
		int rock = 0;
		for (int[] c : tiles)
			if (SETT.TERRAIN().MOUNTAIN.is(c[0], c[1]) || SETT.TERRAIN().CAVE.is(c[0], c[1]))
				rock++;
		// pasture inside a mountain: the rock already carries most of the load, so single pillars suffice
		boolean single = b.cat == SETT.ROOMS().CATS.HUSBANDRY && rock * 2 >= tiles.size();
		final int cx = (bb[0] + bb[2]) / 2, cy = (bb[1] + bb[3]) / 2;

		ArrayList<int[][]> plan = new ArrayList<>();
		for (int y = cy - ((cy - bb[1]) / GRID) * GRID; y <= bb[3]; y += GRID) {
			for (int x = cx - ((cx - bb[0]) / GRID) * GRID; x <= bb[2]; x += GRID) {
				if (!embryo(x, y))
					continue;
				ArrayList<int[]> cl = new ArrayList<>();
				boolean blocked = false;
				for (int i = 0; i < (single ? 1 : 5); i++) {
					int px = x + DX[i], py = y + DY[i];
					if (!embryo(px, py))
						continue;
					if (SETT.ROOMS().fData.item.get(px, py) != null)
						blocked = true;
					cl.add(new int[] {px, py});
				}
				if (!blocked)
					plan.add(cl.toArray(new int[0][]));
			}
		}
		plan.sort(Comparator.comparingInt(c -> Math.abs(c[0][0] - cx) + Math.abs(c[0][1] - cy)));
		queue.clear();
		queue.addAll(plan);
		blueprint = b;
		last = 0;
		return true;
	}

	/** Called every frame the placer tool is active. */
	static void tick() {
		if (queue.isEmpty())
			return;
		RoomPlacer rp = SETT.ROOMS().placement.placer;
		if (rp.blueprint() != blueprint || rp.size() == 0) {
			queue.clear();
			return;
		}
		long now = System.nanoTime();
		if (now - last < DELAY_NS)
			return;
		refresh(rp);
		int guard = 128;
		while (!queue.isEmpty() && guard-- > 0) {
			int[][] cl = queue.poll();
			if (!yellowNear(rp, cl[0][0], cl[0][1]) || !keepsConnected(rp, cl))
				continue;
			remove(rp, cl);
			last = now;
			return;
		}
	}

	/** Forces the cost helper to recompute the yellow map now instead of on the next game tick (matters while paused). */
	private static void refresh(RoomPlacer rp) {
		try {
			Shadow.set(rp.cost(), "tick", Integer.valueOf(-1));
		} catch (Throwable t) {
			Fts.err("pillar refresh", t);
		}
		rp.cost().support();
	}

	private static boolean yellowNear(RoomPlacer rp, int cx, int cy) {
		for (int y = cy - 3; y <= cy + 3; y++)
			for (int x = cx - 3; x <= cx + 3; x++)
				if (embryo(x, y) && rp.cost().is(x, y))
					return true;
		return false;
	}

	/** Removing the cluster must leave the rest of the room in one piece. */
	private static boolean keepsConnected(RoomPlacer rp, int[][] cl) {
		HashSet<Long> gone = new HashSet<>();
		for (int[] c : cl)
			gone.add(key(c[0], c[1]));
		int want = rp.size() - cl.length;
		if (want <= 0)
			return false;
		int sx = -1, sy = -1;
		search:
		for (int[] c : cl)
			for (int i = 1; i < 5; i++) {
				int x = c[0] + DX[i], y = c[1] + DY[i];
				if (embryo(x, y) && !gone.contains(key(x, y))) {
					sx = x;
					sy = y;
					break search;
				}
			}
		if (sx < 0)
			return false;
		ArrayDeque<int[]> q = new ArrayDeque<>();
		HashSet<Long> seen = new HashSet<>();
		q.add(new int[] {sx, sy});
		seen.add(key(sx, sy));
		while (!q.isEmpty()) {
			int[] c = q.poll();
			for (int i = 1; i < 5; i++) {
				int x = c[0] + DX[i], y = c[1] + DY[i];
				if (embryo(x, y) && !gone.contains(key(x, y)) && seen.add(key(x, y)))
					q.add(new int[] {x, y});
			}
		}
		return seen.size() == want;
	}

	private static void remove(RoomPlacer rp, int[][] cl) {
		PlacableMulti undo = (PlacableMulti) rp.area().getUndo();
		int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE;
		for (int[] c : cl) {
			x1 = Math.min(x1, c[0]);
			x2 = Math.max(x2, c[0]);
			y1 = Math.min(y1, c[1]);
			y2 = Math.max(y2, c[1]);
		}
		TileSet area = new TileSet(x1, y1, x2, y2);
		for (int[] c : cl)
			area.add(c[0], c[1]);
		for (int[] c : cl)
			if (undo.isPlacable(c[0], c[1], area, PLACER_TYPE.SQUARE) == null)
				undo.place(c[0], c[1], area, PLACER_TYPE.SQUARE);
		undo.finishPlacing(area);
	}

	private static long key(int x, int y) {
		return ((long) x << 32) | (y & 0xffffffffL);
	}
}
