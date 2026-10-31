package lordapcom.cdream;

import settlement.main.CapitolArea;
import settlement.main.SETT;
import settlement.main.SettlementGrid;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.DIR;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;
import world.WORLD;

/**
 * A capital with at least 8 of its 9 world tiles in the mountains should play as a mountain map. The generator stamps a
 * circle of rock on the mountain sample points of each world tile; at the configured size (radius 35, sample points 128
 * tiles apart on a vanilla map) most of the ground stays open. Here the circles grow until they overlap and every sample
 * point of a mountain tile counts. Paths the generator clears for arrivals and water stay open. Battle maps and capitals
 * with less mountain are untouched.
 */
@Mixin(target = "snake2d.util.file.Json")
public final class MountainFillMixin {

	private static final int MIN_TILES = 8;
	/** At the configured 0.35: radius 105, more than the 91 tiles to the middle of four sample points. */
	private static final double GROW = 3;
	private static final double VANILLA_QUAD = 256;

	/** True from the mountain generator's first line until the next stage (caves) reads its first setting. */
	private static volatile boolean filling;

	// Polyphon can't change a constant in a constructor, so this hooks the settings the generator reads first:
	// game-src settlement/tilemap/generator/GeneratorMountain.java:40 MOUNTAIN_SIZE, GeneratorCave.java:28 CAVE_AMOUNT
	@ModifyReturn(method = "d", params = { String.class, double.class, double.class }, require = true)
	public double size(Object self, double vanilla, String key, double min, double max) {
		try {
			if ("CAVE_AMOUNT".equals(key))
				filling = false;
			else if ("MOUNTAIN_SIZE".equals(key)) {
				filling = fullMountain();
				if (filling)
					return vanilla * GROW * SettlementGrid.QUAD_SIZE / VANILLA_QUAD;
			}
		} catch (Throwable t) {
			Log.once("MountainFillMixin", t);
		}
		return vanilla;
	}

	private static boolean fullMountain() {
		CapitolArea area = SETT.WORLD_AREA();
		if (area.isBattle)
			return false;
		int n = 0;
		for (int i = 0; i < area.ts().size(); i++) {
			COORDINATE c = area.ts().get(i);
			if (WORLD.MOUNTAIN().is(c.x(), c.y()))
				n++;
		}
		return n >= MIN_TILES;
	}

	/** World mountains only flag some of a tile's 9 sample points; while filling, every point of a mountain tile counts. */
	@Mixin(target = "world.map.terrain.WorldMountain$AreaTileMountain")
	public static final class SamplePoints {
		// game-src world/map/terrain/WorldMountain.java AreaTileMountain.is(int,int,DIR)
		@ModifyReturn(method = "is", params = { int.class, int.class, DIR.class }, require = true)
		public boolean all(Object self, boolean vanilla, int tx, int ty, DIR d) {
			try {
				return vanilla || (filling && WORLD.MOUNTAIN().is(tx, ty));
			} catch (Throwable t) {
				Log.once("MountainFillMixin.SamplePoints", t);
				return vanilla;
			}
		}
	}
}
