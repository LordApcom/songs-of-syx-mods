package lordapcom.cdream;

import java.lang.reflect.Method;

import snake2d.PathTile;
import snake2d.PathUtilOnline.Flooder;
import snake2d.util.datatypes.DIR;
import util.GUTIL;
import world.WORLD;
import world.map.pathing.WDirMap;
import world.map.regions.Region;
import world.map.road.WTRAV;

/** Adds path-graph links from an isolated capital to the nearest other regions. Never regenerates anything. */
final class WorldLink {

	private static final int LINKS = 2;

	private WorldLink() {
	}

	static void ensure(int cx, int cy) throws ReflectiveOperationException {
		if (!WORLD.IN_BOUNDS(cx, cy))
			return;
		Region capital = WORLD.REGIONS().player;
		if (reachesAnotherRegion(cx, cy, capital))
			return;

		Method add = WDirMap.class.getDeclaredMethod("add", int.class, int.class, DIR.class);
		add.setAccessible(true);
		Flooder f = GUTIL.flooder();
		f.init(WorldLink.class);
		try {
			f.pushSloppy(cx, cy, 0, null);
			int found = 0;
			while (f.hasMore() && found < LINKS) {
				PathTile t = f.pollSmallest();
				Region r = WORLD.REGIONS().map.get(t);
				if (r != null && r != capital && r.active() && t.isSameAs(r.cx(), r.cy())) {
					link(t, add);
					found++;
				}
				for (DIR d : DIR.ALL) {
					if (!WORLD.IN_BOUNDS(t, d))
						continue;
					// prefer existing graph edges, then passable terrain, then anything (like the game's own connector)
					double v = WORLD.PATH().map.can(t, d) ? 1 : WTRAV.can(t.x(), t.y(), d, false) ? 4 : 20;
					f.pushSmaller(t, d, t.getValue() + v * d.tileDistance(), t);
				}
			}
		} finally {
			f.done();
		}
	}

	private static boolean reachesAnotherRegion(int cx, int cy, Region capital) {
		Flooder f = GUTIL.flooder();
		f.init(WorldLink.class);
		try {
			f.pushSloppy(cx, cy, 0);
			while (f.hasMore()) {
				PathTile t = f.pollSmallest();
				Region r = WORLD.REGIONS().map.get(t);
				if (r != null && r != capital && r.active() && t.isSameAs(r.cx(), r.cy()))
					return true;
				for (DIR d : DIR.ALL)
					if (WORLD.IN_BOUNDS(t, d) && WORLD.PATH().map.can(t, d))
						f.pushSmaller(t, d, t.getValue() + d.tileDistance());
			}
			return false;
		} finally {
			f.done();
		}
	}

	private static void link(PathTile t, Method add) throws ReflectiveOperationException {
		for (PathTile p = t, par = t.getParent(); par != null; p = par, par = p.getParent()) {
			add.invoke(WORLD.PATH().map, p.x(), p.y(), DIR.get(p, par));
			add.invoke(WORLD.PATH().map, par.x(), par.y(), DIR.get(par, p));
		}
	}
}
