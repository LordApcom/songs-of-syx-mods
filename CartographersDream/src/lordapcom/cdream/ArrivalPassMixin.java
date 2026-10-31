package lordapcom.cdream;

import settlement.main.CapitolArea;
import settlement.main.SETT;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.DIR;
import snake2d.util.rnd.Polymap;
import snake2d.util.rnd.RND;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/**
 * The generator keeps arrivals open by cutting four straight lines through the rock from the arrival point, which reads
 * as a big cross on a mountain map. This cuts one winding pass to the nearest map edge instead. It stays wider than the
 * generator's later rock growth (up to 9 tiles, GeneratorMountain.java:209) so the pass can't close. Battle maps keep
 * the cross.
 */
@Mixin(target = "settlement.tilemap.generator.GeneratorMountain")
public final class ArrivalPassMixin {

	private static final int MIN_HALF_WIDTH = 5, MAX_HALF_WIDTH = 9, MAX_DRIFT = 40;

	// game-src settlement/tilemap/generator/GeneratorMountain.java:439 clear(DIR,int,int,Polymap), arrival calls at :153-156.
	// ponytail: a river clear starting exactly at the arrival point is dropped too; the pass and the rest of the river stay.
	@Inject(method = "clear", params = { DIR.class, int.class, int.class, Polymap.class }, at = At.HEAD, cancellable = true, require = true)
	public void clear(Object self, Cancellation ci, DIR d, int x, int y, Polymap p) {
		try {
			CapitolArea area = SETT.WORLD_AREA();
			if (area.isBattle)
				return;
			COORDINATE c = SETT.GRID.tile(area.arrivalTile()).coo(DIR.C);
			if (c.x() != x || c.y() != y)
				return;
			ci.cancel();
			if (d == DIR.N)
				pass(x, y, p);
		} catch (Throwable t) {
			Log.once("ArrivalPassMixin", t);
		}
	}

	private static void pass(int x, int y, Polymap p) {
		int west = x, east = SETT.TWIDTH - 1 - x, north = y, south = SETT.THEIGHT - 1 - y;
		int near = Math.min(Math.min(west, east), Math.min(north, south));
		DIR out = near == west ? DIR.W : near == east ? DIR.E : near == north ? DIR.N : DIR.S;
		DIR side = out.perpendicular();
		int drift = 0, half = MIN_HALF_WIDTH + 2;
		for (int i = 0; i <= near; i++) {
			if (i % 3 == 0) {
				drift += RND.rInt0(3);
				drift = Math.max(-MAX_DRIFT, Math.min(MAX_DRIFT, drift));
				half = Math.max(MIN_HALF_WIDTH, Math.min(MAX_HALF_WIDTH, half + RND.rInt0(1)));
			}
			for (int w = -half; w <= half; w++) {
				int tx = x + out.x() * i + side.x() * (drift + w);
				int ty = y + out.y() * i + side.y() * (drift + w);
				if (SETT.IN_BOUNDS(tx, ty))
					p.checker.set(tx, ty, false);
			}
		}
	}
}
