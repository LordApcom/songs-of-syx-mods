package lordapcom.fts;

import settlement.main.SETT;
import settlement.room.main.RoomBlueprintImp;
import snake2d.util.datatypes.DIR;
import snake2d.util.map.MAP_OBJECT;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/**
 * Feature 1. An indoor plan whose entrance tile faces mountain rock is rejected as "blocked" because
 * rock is solid. The build step already tunnels such entrances, so only this check is relaxed.
 */
@Mixin(target = "settlement.misc.SettPlacability")
public final class HouseMountainMixin {

	// game-src/settlement/misc/SettPlacability.java:73 (only caller: PlacerItemSingle.java:269)
	@ModifyReturn(method = "willBeBlocked", params = {int.class, int.class, int.class, int.class, MAP_OBJECT.class}, require = true)
	public boolean relax(Object self, boolean vanilla, int tx, int ty, int rx, int ry, MAP_OBJECT<?> item) {
		if (!vanilla)
			return false;
		try {
			RoomBlueprintImp b = SETT.ROOMS().placement.placer.blueprint();
			if (b == null || !b.constructor().mustBeIndoors())
				return true;
			for (DIR d : DIR.ORTHO) {
				if (item.is(rx, ry, d))
					continue;
				// One dig must reach walkable ground: a door facing deeper rock would seal the
				// builders inside (seen in game: a house finished with its people cut off from the throne).
				int bx = tx + 2 * d.x(), by = ty + 2 * d.y();
				if (SETT.TERRAIN().MOUNTAIN.is(tx + d.x(), ty + d.y()) && SETT.IN_BOUNDS(bx, by)
						&& !SETT.TERRAIN().MOUNTAIN.is(bx, by) && !SETT.PATH().solidity.is(bx, by))
					return false;
			}
		} catch (Throwable t) {
			Fts.err("house mountain", t);
		}
		return true;
	}
}
