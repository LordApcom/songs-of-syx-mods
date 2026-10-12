package lordapcom.fts;

import java.util.ArrayList;

import settlement.job.Job;
import settlement.job.JobBuildRoad;
import settlement.main.SETT;
import snake2d.KEYCODES;
import view.tool.PlacableMulti;
import view.tool.ToolPlacer;

/**
 * Feature 4. A road job cannot sit on solid rock, so on release each mountain tile gets a tunnel job and is
 * remembered here; once the tile is dug (and its tunnel job is gone) the road job is placed. The list is
 * not saved: after a reload the tunnels still dig, but the roads must be placed again.
 */
final class RoadMountain {

	private static final class Pending {
		final int tx, ty;
		final Job road;

		Pending(int tx, int ty, Job road) {
			this.tx = tx;
			this.ty = ty;
			this.road = road;
		}
	}

	/** Default on every launch; T toggles while a road tool is active. */
	static boolean on = true;

	private static final ArrayList<Pending> pending = new ArrayList<>();
	private static boolean tWas;
	private static int tick;

	private RoadMountain() {
	}

	static boolean canQueue(int tx, int ty) {
		return on && SETT.IN_BOUNDS(tx, ty) && SETT.TERRAIN().MOUNTAIN.is(tx, ty);
	}

	static void queue(int tx, int ty, Job road) {
		PlacableMulti tunnel = SETT.JOBS().clearss.tunnel.placer();
		if (tunnel.isPlacable(tx, ty, null, null) != null)
			return;
		tunnel.place(tx, ty, null, null);
		pending.add(new Pending(tx, ty, road));
	}

	/** T key, only while a road placer is the active tool. */
	static void keys(Object toolPlacer) {
		boolean t = Keys.down(KEYCODES.KEY_T);
		if (t && !tWas && isRoadTool((ToolPlacer) toolPlacer))
			on = !on;
		tWas = t;
	}

	private static boolean isRoadTool(ToolPlacer tool) {
		Object cur = tool.getCurrent();
		if (cur == null)
			return false;
		for (JobBuildRoad r : Lists.of(SETT.JOBS().roads.all))
			if (r.placer() == cur)
				return true;
		return false;
	}

	/** Game tick: moves dug-out tiles from "tunnel" to "road". */
	static void tick() {
		if (pending.isEmpty() || ++tick % 20 != 0)
			return;
		for (int i = pending.size() - 1; i >= 0; i--) {
			Pending p = pending.get(i);
			boolean job = SETT.JOBS().getter.get(p.tx, p.ty) != null;
			if (SETT.TERRAIN().MOUNTAIN.is(p.tx, p.ty)) {
				if (job)
					continue;
				pending.remove(i); // tunnel job erased by the player: the tile can be planned again
			} else if (!job) {
				PlacableMulti road = p.road.placer();
				if (road.isPlacable(p.tx, p.ty, null, null) == null)
					road.place(p.tx, p.ty, null, null);
				pending.remove(i);
			}
		}
	}
}
