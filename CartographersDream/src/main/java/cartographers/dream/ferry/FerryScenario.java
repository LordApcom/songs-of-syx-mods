package cartographers.dream.ferry;

import settlement.main.SETT;
import settlement.room.main.throne.THRONE;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.DIR;

/**
 * Detects whether the current settlement requires a ferry to reach the
 * throne from the map border, and caches a usable spawn/drop/path tuple
 * for the rest of the immigration pipeline.
 *
 * Ferry-required conditions (post v2.13.3 redesign):
 *  1. Some navigable water tile is adjacent to a throne-connected land
 *     tile (= "shore"). This is what the ferry will dock at.
 *  2. Some map-border tile is either navigable water, or walkable land
 *     with an adjacent navigable water tile (= "spawn"). This is where
 *     the boat appears.
 *
 * The path between spawn and shore is a straight DDA line through the
 * water grid. HalfEntity boats are rendered sub-tile and do not collide
 * with terrain — they slide over whatever is beneath the waypoints — so
 * a straight line is both visually plausible (open sea, no obstacles to
 * navigate around) and crash-safe.
 *
 * Why no BFS: prior versions ran a free water-BFS from each border tile
 * looking for a throne-adjacent water tile. On a 1408×1408 mid-sea
 * capital that BFS expanded quadratically through hundreds of thousands
 * of DEEP water tiles, overflowing the Flooder's PathTile pool and
 * tripping the engine's "game has taken too long" watchdog. Bounding
 * the BFS at 5000 visits (v2.13.2) prevented the crash but truncated
 * legitimate paths on big maps — the ferry then never activated and the
 * vanilla "Capital Closed" warning fired on capitals the mod was
 * supposed to support. Replacing the BFS with a single O(W×H) shore
 * scan plus straight-line path is both faster and immune to map size.
 *
 * Computation is lazy and one-shot per settlement. Call reset() if the
 * world is reloaded or terrain changes drastically.
 */
public final class FerryScenario {

	private FerryScenario() {}

	private static boolean computed = false;
	private static boolean active = false;
	private static boolean spawnOnLand = true;  // true: visible land-spawn + wait; false: invisible immediate boat

	private static int spawnTx = -1, spawnTy = -1;
	private static int dropTx = -1, dropTy = -1;
	private static int[] pathX = null;
	private static int[] pathY = null;

	private static boolean shoreSearched = false;
	private static boolean shoreFound = false;
	private static int shoreWaterTx = -1, shoreWaterTy = -1;
	private static int shoreLandTx = -1, shoreLandTy = -1;

	public static void reset() {
		computed = false;
		active = false;
		spawnOnLand = true;
		pathX = null;
		pathY = null;
		shoreSearched = false;
		shoreFound = false;
		shoreWaterTx = -1;
		shoreWaterTy = -1;
		shoreLandTx = -1;
		shoreLandTy = -1;
	}

	public static boolean isActive() {
		// Battle/siege maps (away-battles created via CreateFromWorldMap with
		// isBattle=true) are transient sub-settlements. The ferry state cached
		// here belongs to the player's CAPITAL and is never recomputed for the
		// battle map (entering a battle is not a script "load", so reset() does
		// not fire). Running the ferry deploy path with the capital's stale
		// spawn/drop tiles against a differently-sized enemy map deploys
		// zero-men divisions and crashes the siege. Battle maps must always
		// fall through to vanilla land deployment.
		if (isBattleMap()) return false;
		ensureComputed();
		return active;
	}

	private static boolean isBattleMap() {
		try {
			return SETT.WORLD_AREA().isBattle;
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * The decision point used by shadows that route actors through the
	 * ferry. We only want to ferry actors when vanilla pathing is broken
	 * for THIS specific spawn — otherwise capitals with legitimate land
	 * borders end up funnelling everyone through a fake water route even
	 * though vanilla entry points work fine (Chumpkins's "ferry from the
	 * ocean corner instead of my three land borders" bug). Active vanilla
	 * pathing is signalled by EntryPoints.reachable being non-empty;
	 * EntryPoints.active being non-empty signals walled-off throne (player
	 * cut their own connectivity), which should fall through to vanilla
	 * "Capital Closed" rather than being silently ferry-bypassed.
	 */
	public static boolean shouldFerry() {
		if (!isActive()) return false;
		try {
			settlement.entry.EntryPoints ep = settlement.main.SETT.ENTRY().points;
			return ep.reachable().size() == 0 && ep.active().size() == 0;
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean spawnOnLand() { ensureComputed(); return spawnOnLand; }
	public static int spawnTx() { ensureComputed(); return spawnTx; }
	public static int spawnTy() { ensureComputed(); return spawnTy; }
	public static int dropTx()  { ensureComputed(); return dropTx; }
	public static int dropTy()  { ensureComputed(); return dropTy; }
	public static int[] pathX() { ensureComputed(); return pathX; }
	public static int[] pathY() { ensureComputed(); return pathY; }

	/**
	 * For raider/invasion deployment we need an InvasionSpot rect on a
	 * map-edge stripe. Return which edge the ferry spawn is on so SpotMaker
	 * can build a valid rect there. 0=W, 1=E, 2=N, 3=S, -1=unknown/none.
	 */
	public static int spawnEdge() {
		ensureComputed();
		if (!active) return -1;
		if (spawnTx == 0) return 0;
		if (spawnTx == settlement.main.SETT.TWIDTH - 1) return 1;
		if (spawnTy == 0) return 2;
		if (spawnTy == settlement.main.SETT.THEIGHT - 1) return 3;
		return -1;
	}

	private static void ensureComputed() {
		if (computed) return;
		if (!isPathReady()) return;  // defer; recompute on next call
		computed = true;
		try {
			compute();
		} catch (Throwable t) {
			active = false;
			cartographers.dream.CDLog.info("[CartographersDream] FerryScenario.compute threw: " + t);
			cartographers.dream.CDLog.err(t);
		}
	}

	/**
	 * Returns true once the settlement's PATH supercomponents are populated.
	 * Used to defer compute() until the throne tile reports as
	 * connectivity-true to itself, which is a reliable post-init signal.
	 * Before init, connectivity.is(throne) returns false for every tile and
	 * any compute would cache a wrong negative result.
	 */
	private static boolean isPathReady() {
		try {
			COORDINATE t = THRONE.coo();
			if (t == null) return false;
			return SETT.PATH().connectivity.is(t.x(), t.y());
		} catch (Throwable th) {
			return false;
		}
	}

	private static void compute() {
		cartographers.dream.CDLog.info("[CartographersDream] FerryScenario.compute starting (TWIDTH=" + SETT.TWIDTH + " THEIGHT=" + SETT.THEIGHT + ")");

		// Phase A: locate one shore tile (water adjacent to throne-connected
		// land). Without a shore there is nowhere for the boat to dock and no
		// ferry is possible.
		if (!ensureShoreFound()) {
			active = false;
			cartographers.dream.CDLog.info("[CartographersDream] Ferry scenario INACTIVE (no shore: no navigable water tile is adjacent to a throne-connected land tile)");
			return;
		}
		dropTx = shoreLandTx;
		dropTy = shoreLandTy;

		// Phase B: pick a spawn tile on the map border that minimises path
		// length to the shore. Prefer a border LAND tile (visible land-spawn
		// + wait phase reads more natural for the player). Fall back to a
		// border WATER tile for true open-ocean capitals where every border
		// tile is sea.
		int[] best = findBestBorderLand();
		int spawnWaterTx, spawnWaterTy;
		if (best != null) {
			spawnTx = best[0];
			spawnTy = best[1];
			spawnWaterTx = best[2];
			spawnWaterTy = best[3];
			spawnOnLand = true;
		} else {
			best = findBestBorderWater();
			if (best == null) {
				active = false;
				cartographers.dream.CDLog.info("[CartographersDream] Ferry scenario INACTIVE (no border land-with-adjacent-water and no border navigable water — completely sealed map?)");
				return;
			}
			spawnTx = best[0];
			spawnTy = best[1];
			spawnWaterTx = spawnTx;
			spawnWaterTy = spawnTy;
			spawnOnLand = false;
		}

		// Phase C: straight-line waypoint list between spawn-water and the
		// shore water tile. Boat HalfEntity interpolates sub-tile between
		// waypoints and ignores terrain collision, so a DDA line through the
		// grid is visually plausible (open sea, no obstacles to dodge around)
		// regardless of what tiles it crosses.
		buildStraightLinePath(spawnWaterTx, spawnWaterTy, shoreWaterTx, shoreWaterTy);

		active = true;
		cartographers.dream.CDLog.info("[CartographersDream] Ferry scenario ACTIVE (" + (spawnOnLand ? "land" : "water") + "-spawn): spawn=("
			+ spawnTx + "," + spawnTy + ") drop=(" + dropTx + "," + dropTy + ") shore=(" + shoreWaterTx + "," + shoreWaterTy
			+ ") pathLen=" + (pathX == null ? 0 : pathX.length));
	}

	/**
	 * Scan the entire settlement map for one navigable water tile adjacent
	 * to a throne-connected land tile. Caches the result for subsequent
	 * calls within the same compute() run. O(W×H) in the worst case but
	 * exits early on the first hit; on a real island map there are
	 * thousands of shore tiles so it terminates after a tiny fraction of
	 * the map.
	 */
	private static boolean ensureShoreFound() {
		if (shoreSearched) return shoreFound;
		shoreSearched = true;
		int w = SETT.TWIDTH, h = SETT.THEIGHT;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (!isNavigableWater(x, y)) continue;
				for (int di = 0; di < DIR.ORTHO.size(); di++) {
					DIR d = DIR.ORTHO.get(di);
					int lx = x + d.x();
					int ly = y + d.y();
					if (!SETT.IN_BOUNDS(lx, ly)) continue;
					if (isWalkableLand(lx, ly) && SETT.PATH().connectivity.is(lx, ly)) {
						shoreWaterTx = x;
						shoreWaterTy = y;
						shoreLandTx = lx;
						shoreLandTy = ly;
						shoreFound = true;
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Iterate every map-border tile and return the LAND border tile (with
	 * an adjacent navigable water tile) whose adjacent water is closest by
	 * manhattan distance to the cached shore. Returns
	 * {landX, landY, waterX, waterY} or null if no such border tile exists.
	 */
	private static int[] findBestBorderLand() {
		int bestDist = Integer.MAX_VALUE;
		int[] best = null;
		int w = SETT.TWIDTH, h = SETT.THEIGHT;

		for (int x = 0; x < w; x++) {
			int[] r;
			r = inspectBorderLand(x, 0);     if (r != null) { int d = manhattan(r[2], r[3], shoreWaterTx, shoreWaterTy); if (d < bestDist) { bestDist = d; best = r; } }
			r = inspectBorderLand(x, h - 1); if (r != null) { int d = manhattan(r[2], r[3], shoreWaterTx, shoreWaterTy); if (d < bestDist) { bestDist = d; best = r; } }
		}
		for (int y = 0; y < h; y++) {
			int[] r;
			r = inspectBorderLand(0, y);     if (r != null) { int d = manhattan(r[2], r[3], shoreWaterTx, shoreWaterTy); if (d < bestDist) { bestDist = d; best = r; } }
			r = inspectBorderLand(w - 1, y); if (r != null) { int d = manhattan(r[2], r[3], shoreWaterTx, shoreWaterTy); if (d < bestDist) { bestDist = d; best = r; } }
		}
		return best;
	}

	/**
	 * If (x,y) is a walkable land border tile with an adjacent navigable
	 * water tile, return {x, y, waterX, waterY}. Otherwise null.
	 */
	private static int[] inspectBorderLand(int x, int y) {
		if (!isWalkableLand(x, y)) return null;
		// We do NOT exclude already-throne-connected border land here. On an
		// archipelago with multiple disconnected landmasses, the throne-
		// connected island might extend to a map edge; vanilla EntryPoints
		// will activate that border directly so our ferry never fires for
		// that case anyway (shouldFerry requires active.size == 0). Including
		// it in the search costs nothing and keeps the helper simple.
		for (int di = 0; di < DIR.ORTHO.size(); di++) {
			DIR d = DIR.ORTHO.get(di);
			int wx = x + d.x();
			int wy = y + d.y();
			if (!SETT.IN_BOUNDS(wx, wy)) continue;
			if (!isNavigableWater(wx, wy)) continue;
			return new int[] { x, y, wx, wy };
		}
		return null;
	}

	/**
	 * Iterate every map-border tile and return the WATER border tile
	 * closest by manhattan distance to the cached shore. Returns
	 * {waterX, waterY, waterX, waterY} (duplicated for symmetry with
	 * findBestBorderLand) or null if no border water exists.
	 */
	private static int[] findBestBorderWater() {
		int bestDist = Integer.MAX_VALUE;
		int[] best = null;
		int w = SETT.TWIDTH, h = SETT.THEIGHT;

		for (int x = 0; x < w; x++) {
			if (isNavigableWater(x, 0))     { int d = manhattan(x, 0, shoreWaterTx, shoreWaterTy);     if (d < bestDist) { bestDist = d; best = new int[] { x, 0, x, 0 }; } }
			if (isNavigableWater(x, h - 1)) { int d = manhattan(x, h - 1, shoreWaterTx, shoreWaterTy); if (d < bestDist) { bestDist = d; best = new int[] { x, h - 1, x, h - 1 }; } }
		}
		for (int y = 0; y < h; y++) {
			if (isNavigableWater(0, y))     { int d = manhattan(0, y, shoreWaterTx, shoreWaterTy);     if (d < bestDist) { bestDist = d; best = new int[] { 0, y, 0, y }; } }
			if (isNavigableWater(w - 1, y)) { int d = manhattan(w - 1, y, shoreWaterTx, shoreWaterTy); if (d < bestDist) { bestDist = d; best = new int[] { w - 1, y, w - 1, y }; } }
		}
		return best;
	}

	private static int manhattan(int x1, int y1, int x2, int y2) {
		return Math.abs(x1 - x2) + Math.abs(y1 - y2);
	}

	/**
	 * Generate a DDA-style line between two water tiles, then locally snap
	 * any waypoint that lands on terrain to the nearest navigable-water
	 * tile within a small radius. Boats are HalfEntities that interpolate
	 * sub-tile between waypoints and ignore terrain collision, so a pure
	 * straight line works mechanically — but visually the boat slides
	 * through any peninsula or island that the line happens to clip. The
	 * snap pass nudges those waypoints back into the water without falling
	 * back to an exhaustive BFS (which on a 1408x1408 mid-sea map would
	 * overflow the Flooder's PathTile pool, the original v2.13.0 crash).
	 *
	 * Snap radius is intentionally small (SNAP_RADIUS tiles). If a
	 * waypoint sits inside a thick landmass with no water within radius,
	 * we keep the original tile and accept the visual artefact — better
	 * than diverging the boat hundreds of tiles around a continent and
	 * having immigrants take an hour to arrive.
	 */
	private static final int SNAP_RADIUS = 12;

	private static void buildStraightLinePath(int sx, int sy, int dx, int dy) {
		int dxAbs = Math.abs(dx - sx);
		int dyAbs = Math.abs(dy - sy);
		int len = Math.max(dxAbs, dyAbs) + 1;
		int[] xs = new int[len];
		int[] ys = new int[len];
		if (len == 1) {
			xs[0] = sx;
			ys[0] = sy;
			pathX = xs;
			pathY = ys;
			return;
		}
		for (int i = 0; i < len; i++) {
			double t = (double) i / (len - 1);
			int wx = (int) Math.round(sx + (dx - sx) * t);
			int wy = (int) Math.round(sy + (dy - sy) * t);
			if (!isNavigableWater(wx, wy)) {
				long snapped = findNearestNavigableWater(wx, wy, SNAP_RADIUS);
				if (snapped != -1L) {
					wx = (int) (snapped >> 32);
					wy = (int) (snapped & 0xFFFFFFFFL);
				}
			}
			xs[i] = wx;
			ys[i] = wy;
		}
		pathX = xs;
		pathY = ys;
	}

	/**
	 * Spiral-search outward from (cx,cy) for the closest navigable-water
	 * tile within `radius` tiles (chebyshev). Returns packed (x << 32 | y)
	 * or -1L if none found.
	 */
	private static long findNearestNavigableWater(int cx, int cy, int radius) {
		// r=0 case (defensive — caller already checked but keep symmetric)
		if (isNavigableWater(cx, cy)) {
			return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
		}
		for (int r = 1; r <= radius; r++) {
			// Top and bottom rows of the ring.
			for (int dx = -r; dx <= r; dx++) {
				int x1 = cx + dx, y1 = cy - r;
				if (isNavigableWater(x1, y1)) return ((long) x1 << 32) | (y1 & 0xFFFFFFFFL);
				int y2 = cy + r;
				if (isNavigableWater(x1, y2)) return ((long) x1 << 32) | (y2 & 0xFFFFFFFFL);
			}
			// Left and right columns (excluding corners already covered).
			for (int dy = -r + 1; dy <= r - 1; dy++) {
				int x1 = cx - r, y1 = cy + dy;
				if (isNavigableWater(x1, y1)) return ((long) x1 << 32) | (y1 & 0xFFFFFFFFL);
				int x2 = cx + r;
				if (isNavigableWater(x2, y1)) return ((long) x2 << 32) | (y1 & 0xFFFFFFFFL);
			}
		}
		return -1L;
	}

	private static boolean isWalkableLand(int x, int y) {
		if (!SETT.IN_BOUNDS(x, y)) return false;
		// Land = not water + clearable. Connectivity may still be false (the
		// supercomponent does not extend to the border land in ferry maps),
		// so we explicitly check terrain, not connectivity.
		if (SETT.TERRAIN().WATER.is.is(x, y)) return false;
		if (!SETT.TERRAIN().get(x, y).clearing().isEasilyCleared()) return false;
		return true;
	}

	private static boolean isNavigableWater(int x, int y) {
		if (!SETT.IN_BOUNDS(x, y)) return false;
		// Accept any vanilla water tile (SHALLOW, DEEP, BRIDGE). Originally
		// only DEEP+BRIDGE were accepted under the theory that boats need
		// deep water, but in practice many "island" capitals are surrounded
		// by SHALLOW water (river deltas, lake shores, narrow inlets) and
		// excluding SHALLOW meant the shore scan returned nothing and the
		// ferry stayed silently inactive — vanilla "Capital Closed!" then
		// fired on exactly the capitals the mod exists to support
		// (Workshop report: desert capital surrounded by shallow inlet).
		return SETT.TERRAIN().WATER.is.is(x, y);
	}
}
