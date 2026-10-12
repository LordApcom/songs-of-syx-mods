package lordapcom.cdream;

import static settlement.main.SETT.TERRAIN;

import settlement.main.SETT;
import snake2d.util.rnd.RND;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/**
 * A capital founded on open water or mountain would start with nowhere to land. Right after the settlement map is
 * generated, this clears an irregular patch (islet or plateau) around the start spot. Normal land starts are untouched.
 */
@Mixin(target = "settlement.room.main.throne.Initer")
public final class ThroneCarveMixin {

	private static final int PROBE = 8;
	private static final int RADIUS = 30;
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

			double p1 = RND.rFloat() * Math.PI * 2, p2 = RND.rFloat() * Math.PI * 2;
			int ext = RADIUS + COAST + 2;
			boolean sea = water * 2 > blocked; // islet in the sea, not a plateau in a mountain
			for (int y = ty - ext; y <= ty + ext; y++)
				for (int x = tx - ext; x <= tx + ext; x++) {
					if (!blocked(x, y))
						continue;
					double ang = Math.atan2(y - ty, x - tx);
					double r = RADIUS * (0.8 + 0.2 * Math.sin(3 * ang + p1) + 0.1 * Math.sin(7 * ang + p2));
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

	private static boolean blocked(int x, int y) {
		return SETT.IN_BOUNDS(x, y)
				&& (TERRAIN().WATER.DEEP.is(x, y) || TERRAIN().WATER.SHALLOW.is(x, y) || TERRAIN().MOUNTAIN.is(x, y));
	}
}
