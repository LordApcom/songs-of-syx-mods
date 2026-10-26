package lordapcom.bridge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import settlement.main.SETT;
import view.tool.PlacableFixed;

/**
 * Cartographer's Dream throne carve, tested on a real map: probe a spot the way the mod decides, find spots of each kind,
 * and run the installed mod's carve (lordapcom.cdream.ThroneCarveMixin.carve, by reflection) at a chosen spot.
 */
final class ModCarve {

	private ModCarve() {}

	static void register() {
		Registry.add("cdprobe", "cdprobe <x> <y> - rock/water share within 8 tiles (the mod's trigger: 60%), open tiles connected to the spot within 30 (counted up to 3000), rock within 9 and 30, and whether the landing party fits within 12 tiles", a -> probe(Commands.i(a, 1), Commands.i(a, 2)));
		Registry.add("cdfind", "cdfind [n] - up to n spots each (8-tile grid, away from the throne and map edge) where the mod's trigger fires: 'valley' (250+ open tiles connected), 'small' (1-249) and 'solid' (the spot itself is rock or water)", a -> find(a.length > 1 ? Commands.i(a, 1) : 5));
		Registry.add("cdcarve", "cdcarve <x> <y> - run the installed Cartographer's Dream carve (ThroneCarveMixin.carve) at x,y; answers the probe before and after", a -> carve(Commands.i(a, 1), Commands.i(a, 2)));
	}

	private static boolean blocked(int x, int y) {
		return SETT.IN_BOUNDS(x, y) && (SETT.TERRAIN().WATER.DEEP.is(x, y) || SETT.TERRAIN().WATER.SHALLOW.is(x, y) || SETT.TERRAIN().MOUNTAIN.is(x, y));
	}

	private static int blockedPct(int tx, int ty, int r) {
		int b = 0, t = 0;
		for (int y = -r; y <= r; y++)
			for (int x = -r; x <= r; x++)
				if (x * x + y * y <= r * r) {
					t++;
					if (blocked(tx + x, ty + y))
						b++;
				}
		return 100 * b / t;
	}

	private static int rock(int tx, int ty, int r) {
		int n = 0;
		for (int y = -r; y <= r; y++)
			for (int x = -r; x <= r; x++)
				if (x * x + y * y <= r * r && SETT.IN_BOUNDS(tx + x, ty + y) && SETT.TERRAIN().MOUNTAIN.is(tx + x, ty + y))
					n++;
		return n;
	}

	/** Same flood as the mod (4-connected, open = not rock/water, within a 30-tile square), counted up to cap. */
	private static int open(int tx, int ty, int cap) {
		if (!SETT.IN_BOUNDS(tx, ty) || blocked(tx, ty))
			return 0;
		int R = 30, n = 2 * R + 1, count = 0;
		boolean[] seen = new boolean[n * n];
		ArrayDeque<int[]> q = new ArrayDeque<>();
		q.add(new int[] { tx, ty });
		seen[R * n + R] = true;
		while (!q.isEmpty() && count < cap) {
			int[] c = q.poll();
			count++;
			for (int i = 0; i < 4; i++) {
				int x = c[0] + (i == 0 ? 1 : i == 1 ? -1 : 0), y = c[1] + (i == 2 ? 1 : i == 3 ? -1 : 0);
				int gx = x - tx + R, gy = y - ty + R;
				if (gx < 0 || gy < 0 || gx >= n || gy >= n || seen[gy * n + gx])
					continue;
				seen[gy * n + gx] = true;
				if (SETT.IN_BOUNDS(x, y) && !blocked(x, y))
					q.add(new int[] { x, y });
			}
		}
		return count;
	}

	/** Nearest landing-party centre within r of the spot that the game's own landing checks accept, or null. */
	private static String landFit(int tx, int ty, int r) {
		PlacableFixed p = SETT.PLACERS().landingParty;
		for (int d = 0; d <= r; d++)
			for (int dy = -d; dy <= d; dy++)
				for (int dx = -d; dx <= d; dx++)
					if (Math.max(Math.abs(dx), Math.abs(dy)) == d && Flow.check(p, tx + dx, ty + dy) == null)
						return (tx + dx) + "," + (ty + dy);
		return null;
	}

	private static Object probe(int x, int y) {
		return Commands.m("x", x, "y", y, "blockedPct8", blockedPct(x, y, 8), "open30", open(x, y, 3000), "rock9", rock(x, y, 9),
			"rock30", rock(x, y, 30), "landingFits", landFit(x, y, 12), "landingParty", SETT.PLACERS().landingParty.width() + "x" + SETT.PLACERS().landingParty.height());
	}

	private static Object find(int max) {
		List<Object> valley = new ArrayList<>(), small = new ArrayList<>(), solid = new ArrayList<>();
		int thx = settlement.room.main.throne.THRONE.coo().x(), thy = settlement.room.main.throne.THRONE.coo().y();
		List<int[]> taken = new ArrayList<>();
		for (int y = 48; y < SETT.THEIGHT - 48 && (valley.size() < max || small.size() < max || solid.size() < max); y += 8)
			for (int x = 48; x < SETT.TWIDTH - 48; x += 8) {
				if (Math.hypot(x - thx, y - thy) < 80 || blockedPct(x, y, 8) < 60)
					continue;
				boolean far = true;
				for (int[] t : taken)
					far &= Math.hypot(x - t[0], y - t[1]) > 80; // spots far apart, so carving one leaves the others as they were
				if (!far)
					continue;
				int o = open(x, y, 250);
				List<Object> l = o >= 250 ? valley : o > 0 ? small : solid;
				if (l.size() >= max)
					continue;
				l.add(x + "," + y);
				taken.add(new int[] { x, y });
			}
		return Commands.m("valley", valley, "small", small, "solid", solid);
	}

	private static Object carve(int x, int y) throws Exception {
		Object before = probe(x, y);
		Class<?> c = Class.forName("lordapcom.cdream.ThroneCarveMixin");
		java.lang.reflect.Constructor<?> k = c.getDeclaredConstructor();
		k.setAccessible(true);
		c.getMethod("carve", Object.class, int.class, int.class).invoke(k.newInstance(), null, x, y);
		return Commands.m("before", before, "after", probe(x, y));
	}
}
