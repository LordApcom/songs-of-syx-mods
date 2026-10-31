package lordapcom.cdream;

import static settlement.main.SETT.TERRAIN;

import java.util.ArrayDeque;
import java.util.Arrays;

import settlement.main.SETT;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/**
 * Landing in a pocket walled in by rock leaves the capital closed: nobody can ever arrive. When the throne goes down,
 * this digs the shortest tunnel (fewest rock tiles) from that spot to one of the game's entry points on the map edge,
 * walking free through open ground on the way. A spot that already reaches one loses no rock. Water is never crossed.
 */
@Mixin(target = "settlement.room.main.throne.Initer")
public final class LandingLinkMixin {

	// game-src settlement/misc/placers/PlacerLanding.java:73 ROOMS().THRONE.init.place(tx, ty, 2)
	@Inject(method = "place", params = { int.class, int.class, int.class }, at = At.HEAD, require = true)
	public void link(Object self, int tx, int ty, int rot) {
		try {
			dig(tx, ty);
		} catch (Throwable t) {
			Log.once("LandingLinkMixin", t);
		}
	}

	// ponytail: one pass over the map at landing (2M tiles at most); it never runs during play.
	private static void dig(int tx, int ty) {
		int w = SETT.TWIDTH, h = SETT.THEIGHT;
		int[] cost = new int[w * h], from = new int[w * h];
		Arrays.fill(cost, Integer.MAX_VALUE);
		ArrayDeque<Integer> q = new ArrayDeque<>();
		int start = ty * w + tx, end = -1, edge = -1;
		cost[start] = 0;
		from[start] = -1;
		q.add(start);
		while (!q.isEmpty() && end < 0) {
			int i = q.pollFirst(), x = i % w, y = i / w;
			if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
				if (SETT.ENTRY().points.map.is(x, y)) {
					end = i;
					break;
				}
				if (edge < 0 || cost[i] < cost[edge])
					edge = i;
			}
			for (int d = 0; d < 4; d++) {
				int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0), n = ny * w + nx;
				if (!SETT.IN_BOUNDS(nx, ny) || TERRAIN().WATER.DEEP.is(nx, ny) || TERRAIN().WATER.SHALLOW.is(nx, ny))
					continue;
				int rock = TERRAIN().MOUNTAIN.is(nx, ny) ? 1 : 0;
				if (cost[i] + rock < cost[n]) {
					cost[n] = cost[i] + rock;
					from[n] = i;
					if (rock == 0)
						q.addFirst(n);
					else
						q.addLast(n);
				}
			}
		}
		if (end < 0 && edge >= 0) {
			end = edge; // the map has no entry point this spot can reach by land: open one where the tunnel comes out
			SETT.ENTRY().points.map.set(end % w, end / w, true);
		}
		for (int i = end; i >= 0; i = from[i])
			if (TERRAIN().MOUNTAIN.is(i % w, i / w))
				TERRAIN().NADA.placeFixed(i % w, i / w);
	}
}
