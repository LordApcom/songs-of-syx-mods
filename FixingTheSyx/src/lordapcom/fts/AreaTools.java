package lordapcom.fts;

import java.util.ArrayDeque;

import settlement.main.SETT;
import settlement.room.food.fish.ROOM_FISHERY;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.placement.RoomPlacer;
import view.subview.GameWindow;
import view.tool.PLACER_TYPE;
import view.tool.PlacableMulti;
import vizardalpha.polyphon.api.Shadow;

/** Dispatches Ctrl+click on the room area placer: fishery fill (feature 3) and pillar autofill (feature 5). */
final class AreaTools {

	/** True from a handled click until the mouse button is released. */
	static boolean suppress;

	private AreaTools() {
	}

	static boolean onClick(Object tool, GameWindow window) {
		if (!Keys.ctrl())
			return false;
		RoomPlacer rp = SETT.ROOMS().placement.placer;
		PlacableMulti pm = Shadow.get(tool, "placable");
		RoomBlueprintImp b = rp.blueprint();
		if (b == null || pm == null || pm != rp.area())
			return false;
		int tx = window.tile().x(), ty = window.tile().y();
		if (b instanceof ROOM_FISHERY)
			return SETT.TERRAIN().WATER.SHALLOW.is(tx, ty) && fillWater(pm, rp, tx, ty);
		if (b.constructor().mustBeIndoors() && b.constructor().usesArea())
			return Pillars.start(rp, b, tx, ty);
		return false;
	}

	/**
	 * Feature 3. Breadth-first over connected shallow water, nearest first, so a river longer than one
	 * room allows is filled around the click. Tiles of other rooms are skipped; size and dimension limits
	 * count what is already painted. The tiles then go through the placer's own isPlacable/place.
	 */
	private static boolean fillWater(PlacableMulti pm, RoomPlacer rp, int sx, int sy) {
		final int R = Room.MAX_DIM, N = 2 * R + 1, ox = sx - R, oy = sy - R;
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
		for (int y = oy; y < oy + N; y++)
			for (int x = ox; x < ox + N; x++)
				if (SETT.IN_BOUNDS(x, y) && SETT.ROOMS().placement.embryo.is(x, y)) {
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
		boolean[][] seen = new boolean[N][N];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		ArrayDeque<int[]> accepted = new ArrayDeque<>();
		queue.add(new int[] {sx, sy});
		seen[R][R] = true;
		int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			boolean existing = SETT.ROOMS().placement.embryo.is(c[0], c[1]);
			if (!existing) {
				if (SETT.ROOMS().map.is(c[0], c[1]))
					continue; // owned by another room
				if (rp.size() + accepted.size() + 1 >= Room.MAX_SIZE)
					break;
				int nx1 = Math.min(minX, c[0]), nx2 = Math.max(maxX, c[0]);
				int ny1 = Math.min(minY, c[1]), ny2 = Math.max(maxY, c[1]);
				if (nx2 - nx1 + 1 > Room.MAX_DIM || ny2 - ny1 + 1 > Room.MAX_DIM)
					continue;
				minX = nx1;
				maxX = nx2;
				minY = ny1;
				maxY = ny2;
				accepted.add(c);
			}
			for (int i = 0; i < 4; i++) {
				int x = c[0] + dx[i], y = c[1] + dy[i];
				int gx = x - ox, gy = y - oy;
				if (gx < 0 || gy < 0 || gx >= N || gy >= N || seen[gy][gx] || !SETT.IN_BOUNDS(x, y))
					continue;
				seen[gy][gx] = true;
				if (SETT.TERRAIN().WATER.SHALLOW.is(x, y))
					queue.add(new int[] {x, y});
			}
		}
		if (accepted.isEmpty())
			return true;
		int ax1 = Integer.MAX_VALUE, ay1 = Integer.MAX_VALUE, ax2 = Integer.MIN_VALUE, ay2 = Integer.MIN_VALUE;
		for (int[] c : accepted) {
			ax1 = Math.min(ax1, c[0]);
			ax2 = Math.max(ax2, c[0]);
			ay1 = Math.min(ay1, c[1]);
			ay2 = Math.max(ay2, c[1]);
		}
		TileSet area = new TileSet(ax1, ay1, ax2, ay2);
		for (int[] c : accepted)
			area.add(c[0], c[1]);
		PLACER_TYPE t = PLACER_TYPE.SQUARE;
		pm.finishChecking(area);
		if (pm.isPlacable(area, t) != null)
			return true;
		for (int y = ay1; y <= ay2; y++)
			for (int x = ax1; x <= ax2; x++)
				if (area.is(x, y) && pm.isPlacable(x, y, area, t) == null)
					pm.place(x, y, area, t);
		pm.finishPlacing(area);
		return true;
	}
}
