package world.map.pathing;

import java.lang.reflect.Field;

import game.faction.FACTIONS;
import snake2d.PathTile;
import snake2d.util.datatypes.DIR;
import snake2d.util.misc.ACTION;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import world.WORLD;
import world.map.pathing.WRegFinder.Treaty;
import world.map.regions.Region;
import world.region.RD;

/**
 * Cartographer's Dream: connects an isolated mid-ocean (or otherwise
 * unreachable) player capital to the world-map path graph, so that
 * everything downstream of WORLD.PATH() — caravans, armies, bandits,
 * resource shipments, anything that uses WPath.find — can route to and
 * from the capital.
 *
 * Lives in world.map.pathing for package-private access to:
 *  - WDirMap.add(int, int, DIR) for editing the path graph
 *  - Comps.generate(ACTION, WDirMap) for rebuilding components
 *  - WPATHING.comps for swapping in the regenerated component table
 *
 * Strategy: find the player's capital region centre. If that tile already
 * has any WDirMap edge (it's part of some component), assume vanilla
 * already handled it. Otherwise BFS through neighbouring tiles in a
 * water-friendly manner until we reach a tile that DOES have edges
 * (anchor). Add a chain of edges along the path so the capital tile is
 * merged into the anchor's component. Then regenerate Comps so path-find
 * recognises the merged component immediately.
 *
 * The added edges are persisted in the next save (WDirMap.saver writes
 * them), so re-loads keep the connection. We re-run on load anyway as a
 * defence in case the world state is inconsistent.
 */
public final class CDPathConnector {

	private CDPathConnector() {}

	private static boolean done = false;

	// Damp the "deferring" log spam. Without this, CDPathConnector.run is
	// called every tick by InstanceScript.update, and on the main menu or
	// during shutdown there is no player capital so doRun returns false
	// every call — producing thousands of "no player capital region yet,
	// deferring" lines in the error log (Renegade report). Log the first
	// few attempts so problems are still diagnosable, then go quiet until
	// resetDone() is called (which happens on save load).
	private static int deferLogCount = 0;
	private static final int MAX_DEFER_LOGS = 3;

	public static void resetDone() {
		done = false;
		deferLogCount = 0;
	}

	private static void logDefer(String reason) {
		if (deferLogCount < MAX_DEFER_LOGS) {
			deferLogCount++;
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: " + reason
				+ " (deferring, log " + deferLogCount + "/" + MAX_DEFER_LOGS + ")");
		}
	}

	public static void run() {
		if (done) return;
		try {
			done = doRun();
		} catch (Throwable t) {
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector.run failed");
			cartographers.dream.CDLog.err(t);
			done = true; // do not retry on exception
		}
	}

	// Hard cap on anchors. The world map is small (~250x250) but in theory
	// could have hundreds of coastal regions. 100 covers any reasonable map
	// (was 30, raised after Workshop report of "capital on ocean not
	// connecting to all coastal cities" — even though typical maps have
	// 5-6 coastal neighbours, the 30 cap could clip when Free Lands cells
	// are interleaved between faction-owned regions during the flood).
	private static final int ANCHOR_CAP = 100;

	// Fallback when no shared-ocean anchors are found (lake-locked capital
	// with no water connection to any faction). Falls back to the original
	// "5 nearest factions" behaviour so trade still happens.
	private static final int FALLBACK_NEAREST = 5;

	// Cap on the world-map ocean flood-fill. A 250x250 world is 62500 tiles;
	// a connected ocean usually covers a third or so of that. 20k is enough
	// to traverse any real ocean and well within Java heap headroom.
	private static final int OCEAN_FLOOD_CAP = 20000;

	private static boolean doRun() {
		Region cap = FACTIONS.player() != null ? FACTIONS.player().capitolRegion() : null;
		if (cap == null) {
			logDefer("no player capital region yet");
			return false; // try again next tick
		}

		int cx = cap.cx();
		int cy = cap.cy();
		if (!WORLD.IN_BOUNDS(cx, cy)) {
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: capital coord OOB (" + cx + "," + cy + ")");
			return true;
		}

		// Defensive: WORLD.PATH() should exist whenever a capitol region
		// exists (vanilla initialises WPATHING in WORLD.init before regions
		// resolve), but a misordered third-party mod or hot-load could
		// land us here before WPATHING is ready. Bail and retry next tick
		// rather than NPE through the rest of the function. Caught by
		// round-3 audit.
		if (WORLD.PATH() == null) {
			logDefer("WORLD.PATH() not initialised yet");
			return false;
		}
		WDirMap m = WORLD.PATH().map;
		if (m == null) {
			logDefer("WORLD.PATH().map is null");
			return false;
		}

		// Phase 1: try to bridge to all faction-owned regions whose territory
		// touches the same ocean/river system as the capital. This is what
		// the player expects: "I founded a capital on the coast, I should be
		// able to trade with every coastal city sharing this sea." Without
		// this they'd see distant inland factions get bridged (because of
		// raw manhattan distance) while neighbouring coastal cities did not.
		LIST<Region> anchors = findOceanSharingFactionRegions(cap);

		// Phase 1b: also bridge to every region whose territory directly
		// borders the capital on land. Vanilla road-gen connects ~3 land
		// neighbours; the player typically has 5-6, so the rest stay
		// unreachable for trade/armies. Direct land neighbours (1 hop) only.
		addLandNeighbours(cap, anchors);
		if (anchors.size() == 0) {
			// Phase 2 fallback: no water connection to any faction. Use the
			// original "K nearest" heuristic so a fully lake-locked capital
			// still gets some trade routes instead of being permanently
			// isolated. Rare in practice — typically only happens on
			// completely inland-lake capitals.
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: no ocean-sharing factions found; falling back to nearest-" + FALLBACK_NEAREST);
			anchors = findNearestFactionRegions(cap, cx, cy, FALLBACK_NEAREST);
		} else {
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: " + anchors.size() + " faction regions share an ocean/river system with capital (" + cx + "," + cy + ")");
		}

		if (anchors.size() == 0) {
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: no faction-owned regions found to anchor to (world has only Free Lands?)");
			return true;
		}

		for (Region a : anchors) {
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: bridging cap -> " + a.cx() + "," + a.cy() + " (faction " + (a.faction() == null ? "null" : a.faction().name) + ")");
			bridge(m, cx, cy, a.cx(), a.cy());
		}

		// Rebuild components so path-find sees the merged components.
		WORLD.PATH().comps = Comps.generate(ACTION.NOP, m);

		// Comps.Gen constructor installs a debug overlay (WORLD.OVERLAY().debug)
		// that renders an arrows2 sprite on every WDirMap edge — used during
		// worldgen for visual diagnostics. Vanilla WPATHING.load and .generate
		// clear it back to null immediately after Comps.generate; we have to
		// do the same or the overlay stays installed for the entire game
		// session, drawing orange X marks across every road on the world map
		// (Workshop reports of "your mod added paths everywhere that pollute
		// the world map even without overlays toggled").
		world.WORLD.OVERLAY().debug = null;

		// Vanilla RDDistance caches "which factions are reachable from
		// player capital" and only re-checks when its private bDirty flag
		// is set true (by diplomacy events, region ownership change, or
		// save/load). Modifying WDirMap directly does NOT trigger that.
		// Force the recompute via reflection so the diplomacy UI ("Distant
		// Faction" => "Near Faction") and shipment-eligibility queries
		// see our new bridges.
		try {
			Field bDirty = RD.DIST().getClass().getDeclaredField("bDirty");
			bDirty.setAccessible(true);
			bDirty.setBoolean(RD.DIST(), true);
		} catch (Throwable t) {
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: could not force RDDistance.bDirty true");
			cartographers.dream.CDLog.err(t);
		}

		// Sanity check: for each anchor, log whether path-find succeeds in
		// both directions (cap -> anchor for outbound trade, anchor -> cap
		// for inbound trade). Asymmetric failure here points at a Comps
		// merge bug or a directional issue in the path finder.
		for (Region a : anchors) {
			PathTile out = WORLD.PATH().path(cx, cy, a.cx(), a.cy(), Treaty.DUMMY);
			PathTile in  = WORLD.PATH().path(a.cx(), a.cy(), cx, cy, Treaty.DUMMY);
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: post-bridge check anchor (" + a.cx() + "," + a.cy() + ") outbound=" + (out != null) + " inbound=" + (in != null));
		}

		cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: all bridges complete, components regenerated, RDDistance marked dirty");
		return true;
	}

	private static boolean hasAnyEdge(WDirMap m, int tx, int ty) {
		for (int di = 0; di < DIR.ALL.size(); di++) {
			if (m.can(tx, ty, DIR.ALL.get(di))) return true;
		}
		return false;
	}

	/**
	 * Flood-fill from a tile of big-water adjacent to the capital across
	 * all connected big-water tiles (oceans + connected rivers/lakes) and
	 * return every faction-owned region whose territory touches that
	 * connected water body. Excludes the player's own faction.
	 *
	 * If the capital is not adjacent to any big-water tile (truly inland
	 * capital), returns an empty list and the caller falls back to the
	 * nearest-K heuristic.
	 *
	 * Hard-capped at ANCHOR_CAP regions to prevent visual clutter and
	 * OCEAN_FLOOD_CAP tiles to prevent the flood-fill from running away
	 * on pathological maps.
	 */
	private static LIST<Region> findOceanSharingFactionRegions(Region capital) {
		ArrayList<Region> result = new ArrayList<>(ANCHOR_CAP);

		// Find a seed big-water tile adjacent to (or inside) the capital
		// region's bounding box. Capitals founded on tiny islands often
		// have water adjacent to their centre tile; capitals on larger
		// landmasses need a wider search.
		int seedX = -1, seedY = -1;
		int cx = capital.cx();
		int cy = capital.cy();
		// Search radius bumped from 8 to 30 — Workshop report of "capital
		// on the ocean still not connecting to coastal cities". 8 is too
		// tight for capitals whose region centre falls inland of the coast
		// (which is most of them: regions are 10-15 tiles wide). 30 covers
		// every reasonable region width on the world map and any nearby
		// island corridor between capital and water.
		int searchRadius = 0;
		findSeed:
		while (searchRadius <= 30) {
			for (int dy = -searchRadius; dy <= searchRadius; dy++) {
				for (int dx = -searchRadius; dx <= searchRadius; dx++) {
					if (Math.abs(dx) != searchRadius && Math.abs(dy) != searchRadius) continue;
					int tx = cx + dx;
					int ty = cy + dy;
					if (!WORLD.IN_BOUNDS(tx, ty)) continue;
					if (WORLD.WATER().isBig.is(tx, ty)) {
						seedX = tx;
						seedY = ty;
						break findSeed;
					}
				}
			}
			searchRadius++;
		}

		if (seedX < 0) {
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: no big-water within 30 tiles of capital (" + cx + "," + cy + "), treating as inland");
			return result;
		}
		cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: seed water at (" + seedX + "," + seedY + "), radius " + searchRadius);

		// BFS over big-water tiles. During the BFS, for each water tile we
		// look at its non-water neighbours and record any region they belong
		// to (other than the player's). The region.cx,cy might not be on the
		// coast but as long as ANY tile of the region touches our water, the
		// region is "ocean-sharing" and we bridge to its centre.
		int w = WORLD.TWIDTH();
		int h = WORLD.THEIGHT();
		boolean[] visitedWater = new boolean[w * h];
		boolean[] seenRegion = new boolean[w * h];  // dedup by region.cTile() index
		int[] queueX = new int[OCEAN_FLOOD_CAP];
		int[] queueY = new int[OCEAN_FLOOD_CAP];
		int head = 0, tail = 0;

		queueX[tail] = seedX;
		queueY[tail] = seedY;
		tail++;
		visitedWater[seedX + seedY * w] = true;

		int visited = 0;
		while (head < tail) {
			if (visited++ >= OCEAN_FLOOD_CAP) {
				cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: ocean flood-fill hit cap " + OCEAN_FLOOD_CAP + ", proceeding with " + result.size() + " anchors so far");
				break;
			}
			int tx = queueX[head];
			int ty = queueY[head];
			head++;

			// Check this water tile's 4 neighbours. If a neighbour is
			// non-water, see if it belongs to a faction region we haven't
			// recorded yet. If a neighbour is water and unvisited, enqueue.
			for (int di = 0; di < DIR.ORTHO.size(); di++) {
				DIR d = DIR.ORTHO.get(di);
				int nx = tx + d.x();
				int ny = ty + d.y();
				if (!WORLD.IN_BOUNDS(nx, ny)) continue;
				int ni = nx + ny * w;

				if (WORLD.WATER().isBig.is(nx, ny)) {
					if (!visitedWater[ni] && tail < OCEAN_FLOOD_CAP) {
						visitedWater[ni] = true;
						queueX[tail] = nx;
						queueY[tail] = ny;
						tail++;
					}
				} else {
					// Land tile. See which region it belongs to.
					Region r = WORLD.REGIONS().map.get(nx, ny);
					if (r == null) continue;
					if (r == capital) continue;

					// Dedup-by-region BEFORE filtering so a region we skip
					// is only logged once instead of once per coastal tile.
					int regKey = r.cx() + r.cy() * w;
					if (regKey < 0 || regKey >= seenRegion.length) continue;
					if (seenRegion[regKey]) continue;
					seenRegion[regKey] = true;

					if (r.faction() == null) {
						cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector:   touched coastal region '" + r + "' SKIPPED (Free Lands, no faction)");
						continue;
					}
					if (r.faction() == FACTIONS.player()) {
						cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector:   touched coastal region '" + r + "' SKIPPED (player faction)");
						continue;
					}
					if (r.info.area() == 0) {
						cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector:   touched coastal region '" + r + "' SKIPPED (area=0)");
						continue;
					}

					cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector:   touched coastal region '" + r + "' (faction " + r.faction().name + ") at land tile (" + nx + "," + ny + ") -> bridge");
					result.add(r);
					if (result.size() >= ANCHOR_CAP) {
						cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: anchor cap " + ANCHOR_CAP + " reached during flood-fill, stopping");
						return result;
					}
				}
			}
		}

		// Debug: iterate all world regions and list the ones that are coastal
		// (touch any big-water tile) but were NOT reached by the flood. Helps
		// confirm whether unconnected coastal cities are in a separate water
		// body or whether the flood missed them.
		try {
			for (Region r : WORLD.REGIONS().all()) {
				if (r == capital) continue;
				if (r.faction() == null) continue;
				if (r.faction() == FACTIONS.player()) continue;
				if (r.info.area() == 0) continue;
				int regKey = r.cx() + r.cy() * w;
				if (regKey >= 0 && regKey < seenRegion.length && seenRegion[regKey]) continue;
				// Region not reached. Is it coastal? Scan its tiles for any
				// water-adjacent land tile.
				boolean coastal = false;
				int sampleWx = -1, sampleWy = -1;
				outer:
				for (int ty = 0; ty < h; ty++) {
					for (int tx = 0; tx < w; tx++) {
						if (WORLD.REGIONS().map.get(tx, ty) != r) continue;
						for (int di = 0; di < DIR.ORTHO.size(); di++) {
							DIR d = DIR.ORTHO.get(di);
							int wx = tx + d.x();
							int wy = ty + d.y();
							if (!WORLD.IN_BOUNDS(wx, wy)) continue;
							if (WORLD.WATER().isBig.is(wx, wy)) {
								coastal = true;
								sampleWx = wx;
								sampleWy = wy;
								break outer;
							}
						}
					}
				}
				if (coastal) {
					boolean inFlood = sampleWx >= 0 && visitedWater[sampleWx + sampleWy * w];
					cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector:   UNREACHED coastal region '"
						+ r + "' (faction " + r.faction().name + ") - sample water tile (" + sampleWx + "," + sampleWy
						+ ") " + (inFlood ? "IS in flood but region was filtered?!" : "in SEPARATE water body"));
				}
			}
		} catch (Throwable t) {
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector: unreached-coastal scan failed: " + t);
		}

		return result;
	}

	/**
	 * Phase 1b: scan the capital's tiles and add any directly-bordering
	 * faction-owned region to `anchors`. Cheap (single map pass). Duplicates
	 * with ocean-flood results are skipped via reference equality + faction
	 * region centre dedup.
	 */
	private static void addLandNeighbours(Region capital, LIST<Region> anchorsRO) {
		// Up-cast: we know findOceanSharingFactionRegions returns ArrayList.
		@SuppressWarnings("unchecked")
		ArrayList<Region> anchors = (ArrayList<Region>) anchorsRO;
		int w = WORLD.TWIDTH();
		int h = WORLD.THEIGHT();
		ArrayList<Region> found = new ArrayList<>(8);
		for (int ty = 0; ty < h; ty++) {
			for (int tx = 0; tx < w; tx++) {
				if (WORLD.REGIONS().map.get(tx, ty) != capital) continue;
				for (int di = 0; di < DIR.ORTHO.size(); di++) {
					DIR d = DIR.ORTHO.get(di);
					int nx = tx + d.x();
					int ny = ty + d.y();
					if (!WORLD.IN_BOUNDS(nx, ny)) continue;
					Region r = WORLD.REGIONS().map.get(nx, ny);
					if (r == null || r == capital) continue;
					if (r.faction() == null) continue;
					if (r.faction() == FACTIONS.player()) continue;
					if (r.info.area() == 0) continue;
					if (containsRegion(anchors, r) || containsRegion(found, r)) continue;
					found.add(r);
				}
			}
		}
		for (Region r : found) {
			cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector:   land neighbour '"
				+ r + "' (faction " + r.faction().name + ") -> bridge");
			anchors.add(r);
		}
	}

	private static boolean containsRegion(ArrayList<Region> list, Region r) {
		for (Region o : list) if (o == r) return true;
		return false;
	}

	/**
	 * Return the K nearest non-player region centres that are owned by a
	 * Faction (excluding Free Lands / null-faction regions, since those
	 * don't produce trade caravans and can't bring the player into the
	 * main faction network).
	 *
	 * If fewer than K faction-owned regions exist, returns however many
	 * were found. If none, returns empty.
	 */
	private static LIST<Region> findNearestFactionRegions(Region capital, int sx, int sy, int k) {
		Region[] best = new Region[k];
		double[] bestD = new double[k];
		for (int i = 0; i < k; i++) bestD[i] = Double.MAX_VALUE;

		for (Region r : WORLD.REGIONS().all()) {
			if (r == capital) continue;
			if (r.info.area() == 0) continue;
			if (r.faction() == null) continue;  // skip Free Lands
			if (r.faction() == FACTIONS.player()) continue;
			int dx = r.cx() - sx;
			int dy = r.cy() - sy;
			double d = (double) dx * dx + (double) dy * dy;
			// Insert into top-K (sorted descending by distance, so last is worst).
			for (int i = 0; i < k; i++) {
				if (d < bestD[i]) {
					// Shift the rest right.
					for (int j = k - 1; j > i; j--) {
						bestD[j] = bestD[j - 1];
						best[j] = best[j - 1];
					}
					bestD[i] = d;
					best[i] = r;
					break;
				}
			}
		}

		ArrayList<Region> result = new ArrayList<>(k);
		for (int i = 0; i < k; i++) {
			if (best[i] != null) result.add(best[i]);
		}
		return result;
	}

	/**
	 * Add WDirMap edges along a Bresenham-ish straight line from
	 * (sx, sy) toward (tx, ty), one ortho/diagonal step at a time. This
	 * is intentionally simple — it doesn't preserve "water only" semantics,
	 * but since the path crosses tiles that don't already have edges, we
	 * just stamp our own. Caravans/armies will route through these as if
	 * they were a generated trade route.
	 */
	private static void bridge(WDirMap m, int sx, int sy, int tx, int ty) {
		int x = sx;
		int y = sy;
		int steps = 0;
		while (x != tx || y != ty) {
			if (++steps > 4096) {
				cartographers.dream.CDLog.info("[CartographersDream] CDPathConnector.bridge: step cap exceeded, aborting at (" + x + "," + y + ")");
				return;
			}
			DIR d = pickDir(x, y, tx, ty);
			if (d == null) return;
			m.add(x, y, d);  // also adds reverse edge from (x+dx, y+dy) back
			x += d.x();
			y += d.y();
		}
	}

	private static DIR pickDir(int x, int y, int tx, int ty) {
		int dx = Integer.compare(tx, x); // -1, 0, +1
		int dy = Integer.compare(ty, y);
		if (dx == 0 && dy == 0) return null;
		// Prefer diagonal if both axes differ.
		for (int di = 0; di < DIR.ALL.size(); di++) {
			DIR d = DIR.ALL.get(di);
			if (d.x() == dx && d.y() == dy) return d;
		}
		return null;
	}
}
