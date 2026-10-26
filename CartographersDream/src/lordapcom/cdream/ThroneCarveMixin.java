package lordapcom.cdream;

import static settlement.main.SETT.TERRAIN;

import java.util.ArrayDeque;

import settlement.main.SETT;
import snake2d.util.rnd.RND;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/**
 * A capital founded on open water or mountain would start with nowhere to land. Right after the settlement map is
 * generated, this clears an irregular patch (islet or plateau) around the start spot. Normal land starts are untouched.
 * A mountain start that already has room to land (a valley or ledge) keeps every rock tile: players found mountain
 * capitals on purpose, so the plateau is only a small pocket and only when there is no ground at all. Sea starts
 * still get the full islet.
 */
@Mixin(target = "settlement.room.main.throne.Initer")
public final class ThroneCarveMixin {

	private static final int PROBE = 8;
	private static final int RADIUS = 30;
	/** Mountain start with no ground: a pocket for the throne and the first stockpile, not a plateau. */
	private static final int POCKET = 9;
	/** Open tiles connected to the throne (within RADIUS) that count as "room to land": about a 9-tile radius. */
	private static final int MIN_OPEN = 250;
	private static final int BORDER = 6;
	private static final int COAST = 2;

	// game-src settlement/room/main/throne/Initer.java:42 markArround(int,int), called from SETT.java:366
	@Inject(method = "markArround", params = { int.class, int.class }, at = At.HEAD, require = true)
	public void carve(Object self, int tx, int ty) {
		try {
			int blocked = 0, total = 0, water = 0;
			for (int y = -PROBE; y <= PROBE; y++)
				for (int x = -PROBE; x <= PROBE; x++)
					if (x * x + y * y <= PROBE * PROBE) {
						total++;
						if (blocked(tx + x, ty + y))
							blocked++;
						if (TERRAIN().WATER.DEEP.is(tx + x, ty + y) || TERRAIN().WATER.SHALLOW.is(tx + x, ty + y))
							water++;
					}
			if (blocked * 10 < total * 6)
				return;
			boolean sea = water * 2 > blocked; // islet in the sea, not a plateau in a mountain
			if (!sea && openAround(tx, ty) >= MIN_OPEN)
				return; // a valley or ledge in the mountains: already somewhere to land, keep the rock

			double p1 = RND.rFloat() * Math.PI * 2, p2 = RND.rFloat() * Math.PI * 2;
			int radius = sea ? RADIUS : POCKET;
			int ext = radius + COAST + 2;
			for (int y = ty - ext; y <= ty + ext; y++)
				for (int x = tx - ext; x <= tx + ext; x++) {
					if (!blocked(x, y))
						continue;
					double ang = Math.atan2(y - ty, x - tx);
					double r = radius * (0.8 + 0.2 * Math.sin(3 * ang + p1) + 0.1 * Math.sin(7 * ang + p2));
					double d = Math.hypot(x - tx, y - ty);
					if (d < r)
						TERRAIN().NADA.placeFixed(x, y);
					else if (sea && d < r + COAST && TERRAIN().WATER.DEEP.is(x, y))
						TERRAIN().WATER.SHALLOW.placeFixed(x, y); // a shallow beach ring, like natural coasts
				}
			if (sea)
				deepBorder();
		} catch (Throwable t) {
			Log.once("ThroneCarveMixin", t);
		}
	}

	/**
	 * Sea capital: the map's border band becomes deep water, so nobody arriving from the edge stands in the shallows
	 * there. Shallows within COAST tiles of land stay, so real coasts that reach the edge keep their beach.
	 */
	private static void deepBorder() {
		final int w = SETT.TWIDTH, h = SETT.THEIGHT;
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				if (x >= BORDER && y >= BORDER && x < w - BORDER && y < h - BORDER)
					continue;
				if (TERRAIN().WATER.SHALLOW.is(x, y) && !nearLand(x, y))
					TERRAIN().WATER.DEEP.placeFixed(x, y);
			}
	}

	private static boolean nearLand(int x, int y) {
		for (int dy = -COAST; dy <= COAST; dy++)
			for (int dx = -COAST; dx <= COAST; dx++)
				if (SETT.IN_BOUNDS(x + dx, y + dy) && !TERRAIN().WATER.DEEP.is(x + dx, y + dy)
						&& !TERRAIN().WATER.SHALLOW.is(x + dx, y + dy))
					return true;
		return false;
	}

	/** Open (not water, not rock) tiles 4-connected to the start, counted within RADIUS; stops at MIN_OPEN. */
	private static int openAround(int tx, int ty) {
		if (!SETT.IN_BOUNDS(tx, ty) || blocked(tx, ty))
			return 0;
		int n = 2 * RADIUS + 1;
		boolean[] seen = new boolean[n * n];
		ArrayDeque<int[]> q = new ArrayDeque<>();
		q.add(new int[] { tx, ty });
		seen[RADIUS * n + RADIUS] = true;
		int count = 0;
		while (!q.isEmpty() && count < MIN_OPEN) {
			int[] c = q.poll();
			count++;
			for (int i = 0; i < 4; i++) {
				int x = c[0] + (i == 0 ? 1 : i == 1 ? -1 : 0), y = c[1] + (i == 2 ? 1 : i == 3 ? -1 : 0);
				int gx = x - tx + RADIUS, gy = y - ty + RADIUS;
				if (gx < 0 || gy < 0 || gx >= n || gy >= n || seen[gy * n + gx])
					continue;
				seen[gy * n + gx] = true;
				if (SETT.IN_BOUNDS(x, y) && !blocked(x, y))
					q.add(new int[] { x, y });
			}
		}
		return count;
	}

	private static boolean blocked(int x, int y) {
		return SETT.IN_BOUNDS(x, y)
				&& (TERRAIN().WATER.DEEP.is(x, y) || TERRAIN().WATER.SHALLOW.is(x, y) || TERRAIN().MOUNTAIN.is(x, y));
	}
}
