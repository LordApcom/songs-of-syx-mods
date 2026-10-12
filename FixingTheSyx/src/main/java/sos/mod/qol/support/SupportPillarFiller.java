package sos.mod.qol.support;

import settlement.main.SETT;
import settlement.room.food.pasture.ROOM_PASTURE;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.construction.ConstructionData;
import snake2d.LOG;
import snake2d.MButt;
import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.RECTANGLE;
import view.keyboard.KEYS;
import view.main.VIEW;
import view.subview.GameWindow;
import view.tool.PLACABLE;
import view.tool.PLACER_TYPE;
import view.tool.PlacableMulti;

/**
 * Greedy auto-fill of internal support pillars for indoor area-based rooms. Drives the
 * algorithm asynchronously: places at most ONE pillar per tick so vanilla
 * {@code UtilExtraCost.cache} can recompute {@code dExpensive} between placements (it only
 * runs on tick boundaries). Triggered by either {@link #request()} (called from the UI
 * button) or by Ctrl+Click while the room placer is active (handled here).
 */
public final class SupportPillarFiller {

    /**
     * Spacing between pillar centres. Same value for outdoor and mountain — the outdoor
     * "+" cluster's arms extend the effective reach by 1 tile, balancing out vanilla's
     * weaker outdoor support so the grids end up covering at the same density.
     */
    private static final int SPACING = 8;

    /**
     * Spacing for rooms carved into MOUNTAIN. Tighter than {@link #SPACING} because
     * mountain rooms get no autoWalls edge support to mask gaps and a 1x1 pillar's
     * vanilla support reach is only ~3.5 tiles: at spacing 8 a cell centre sits 8/√2 ≈
     * 5.7 tiles from the four surrounding pillars, i.e. zero support → it stays yellow
     * (the "support tiles inside mountains leave yellow areas" Workshop report). At
     * spacing 5 the centre is ≈3.5 tiles away, within reach of the ring of pillars.
     * ponytail: 5 covers the vast majority; drop to 4 if any mid-cell yellows are still
     * reported (4/√2 ≈ 2.8, fully within reach, at the cost of denser pillars).
     */
    private static final int SPACING_MOUNTAIN = 5;

    /**
     * Score radius for MOUNTAIN rooms (1x1 pillar). Matches vanilla support reach + a tile
     * of headroom — claims yellows up to 5 tiles from the centre when deciding whether
     * placing here would still be useful.
     */
    private static final int SCORE_RADIUS_MOUNTAIN = 5;

    /**
     * Score radius for OUTDOOR rooms ("+" cluster). The cross has 1-tile arms, so its
     * actual support footprint extends one tile farther in each ortho direction than a
     * lone centre. Using {@code SPACING/2 + 1 = 4 + 1 = 5 ish} → 6 captures yellows that
     * the arm tiles will resolve and prevents the grid from over-placing on top of them.
     */
    private static final int SCORE_RADIUS_OUTDOOR = 6;

    /**
     * Minimum wall-distance for MOUNTAIN rooms (1x1 pillar). The mountain face around the
     * room already supports tiles within ~3 of itself, so a 1x1 pillar closer than this is
     * wasted — vanilla support already covers that tile.
     */
    private static final int MIN_WALL_DIST_MOUNTAIN = 3;

    /**
     * Minimum wall-distance for OUTDOOR rooms ("+" cluster). Set to 1 because the autoWalls
     * boundary supports only ~1 tile inwards (much weaker than a mountain face), so the grid
     * has to reach almost to the edge to cover yellows near curved walls. The "+" cluster
     * handles this gracefully: arms that fall onto the wall are dropped at placement time.
     */
    private static final int MIN_WALL_DIST_OUTDOOR = 1;

    /** Cap on the wall-distance search; candidates further than this all score equally. */
    private static final int MAX_WALL_DIST = 8;

    /**
     * Ticks between placements. Vanilla AI/worker plans and {@code UtilExtraCost.cache}
     * need breathing room between shrinks — placing once per tick floods both and causes
     * "Blocked by terrain!" rejections plus NPEs in worker pathing. ~0.5s feels deliberate.
     */
    private static final int PLACEMENT_PERIOD_TICKS = 30;

    /** Greedy work is in progress. While true, every tick continues placing pillars. */
    private boolean running = false;
    /** Iteration cap: set to {@code plannedPillars.size()} after {@link #computePillarPlan}. */
    private int iterationsLeft = 0;
    /** Detect Ctrl+Click rising edge. */
    private boolean prevLeftDown = false;
    /** Increments every {@link #runFrame}; throttles placements to {@link #PLACEMENT_PERIOD_TICKS}. */
    private int tickCounter = 0;

    /**
     * Pillar plan precomputed at request time against the ORIGINAL embryo state. Each entry
     * is {@code {cx, cy, score}} and represents the CENTRE of a pillar cluster — the actual
     * tiles placed are derived by adding each {@link #pillarOffsets} entry to the centre.
     * Iterating this list (instead of rescoring every tick) avoids the "drift" bug where
     * previously-placed pillars contaminate the wall map used for centrality, pushing later
     * candidates into corners that the walls already support.
     */
    private final java.util.ArrayList<int[]> plannedPillars = new java.util.ArrayList<>();
    /** Cursor into {@link #plannedPillars}; entries before this have already been processed. */
    private int plannedIndex = 0;

    /**
     * Cluster shape for each planned pillar, decided once per request by sampling the
     * embryo's terrain in {@link #computePillarPlan}. Each row is {@code {dx, dy}} relative
     * to a plan entry's centre. Mountain rooms use {@code {{0,0}}} (vanilla 1x1 pillars
     * already give strong support). Outdoor walled-in rooms use {@code {{0,0},{1,0}}} (1x2
     * horizontal cluster) because vanilla's {@code TileRayTracer.check} only uses each
     * unique offset once per ray, so a single tile contributes to far fewer support rays
     * outside the mountain.
     */
    private int[][] pillarOffsets = new int[][] {{0, 0}};

    public void tick() {
        if (VIEW.s() == null || !VIEW.s().isActive()) {
            running = false;
            prevLeftDown = false;
            return;
        }

        // Ctrl+Click hotkey rising edge.
        boolean leftDown = MButt.LEFT.isDown();
        if (leftDown && !prevLeftDown && KEYS.MAIN().MOD.isPressed()) {
            tryRequestFromHotkey();
        }
        prevLeftDown = leftDown;

        if (!running) return;
        runFrame();
    }

    /** Called by the UI button or by the hotkey path when the player asks for fill. */
    public void request() {
        if (!isRoomEligible()) return;
        Object placer = SupportReflect.roomPlacer();
        AREA embryo = SupportReflect.instanceOf(placer);
        if (embryo == null || embryo.area() == 0) return;
        if (!yellowsRemain(embryo)) return;
        running = true;
        tickCounter = 0;
        plannedPillars.clear();
        plannedIndex = 0;
        computePillarPlan(embryo, blueprintOf(placer));
        // Iteration cap counts ATTEMPTS (one per cluster placement OR fallback pass), not
        // individual tiles — a "+" cluster places 5 tiles in one attempt. The +50 budget
        // gives the fallback greedy room to mop up yellows the grid couldn't reach (e.g.
        // along the curved edge of an oval pasture).
        iterationsLeft = plannedPillars.size() + 50;
        LOG.ln("[FixingTheSyx] support pillars: requested, cap=" + iterationsLeft
                + ", planned=" + plannedPillars.size());
    }

    /**
     * Precomputes the FULL pillar placement plan against the original embryo using a
     * SYMMETRIC grid centred on the room's geometric centre. Pillars are placed on a
     * regular {@link #SPACING}-spaced lattice anchored at the centre, walked outward in
     * concentric Chebyshev rings so symmetric pairs are emitted together. This trades
     * "cover every last yellow" (the previous set-cover behaviour) for a clean,
     * predictable pattern the player can read at a glance — symmetry over completeness.
     *
     * <p>The plan is still computed once against the ORIGINAL embryo so previously placed
     * pillars cannot contaminate scoring (the "drifting pillar" bug).
     *
     * <p>No straggler-fallback inside the plan: if a grid slot is invalid (would
     * disconnect, too close to a wall, no uncovered yellow nearby), we skip it rather
     * than search for a nearby substitute — that would break the pattern.
     */
    private void computePillarPlan(AREA embryo, RoomBlueprintImp blueprint) {
        plannedPillars.clear();
        plannedIndex = 0;
        RECTANGLE body = embryo.body();

        // 0. Detect terrain type. Mountain terrain matters because a single mountain tile
        //    blocks many vanilla support rays — for a PASTURE in mountain, 1x1 pillars on
        //    a wide grid suffice. For every other indoor area-based room (workshops,
        //    refineries, libraries, etc.) the player wants a tighter "+" pattern even when
        //    the room is carved into rock, because those rooms tend to be smaller and the
        //    "+" symmetry reads more cleanly on screen. So the 1x1 path is now gated on
        //    BOTH "mostly-mountain terrain" AND "blueprint is pasture".
        int mountainTiles = 0;
        int sampledTiles = 0;
        for (int y = body.y1(); y <= body.y2(); y++) {
            for (int x = body.x1(); x <= body.x2(); x++) {
                if (!embryo.is(x, y)) continue;
                sampledTiles++;
                if (SETT.TERRAIN().MOUNTAIN.is(x, y)) mountainTiles++;
            }
        }
        boolean isMountainTerrain = (sampledTiles > 0) && (mountainTiles * 2 > sampledTiles);
        boolean isPasture = (blueprint instanceof ROOM_PASTURE);
        boolean usePackedGrid = isMountainTerrain && isPasture;
        // "Packed grid" = 1x1 pillars far apart (mountain pasture). Everything else uses
        // the "+" cluster (centre + 4 ortho arms): outdoor rooms always need it because
        // vanilla's TileRayTracer.check dedups offsets per ray, and indoor non-pasture
        // rooms benefit from the tighter symmetric coverage too.
        pillarOffsets = usePackedGrid
                ? new int[][] {{0, 0}}
                : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        // Any mountain room (not just the 1x1 pasture path) needs the tighter mountain
        // spacing — non-pasture mountain rooms use the "+" cluster but still relied on the
        // too-wide SPACING=8 grid, leaving the same interior yellows.
        int spacing = isMountainTerrain ? SPACING_MOUNTAIN : SPACING;
        int scoreRadius = usePackedGrid ? SCORE_RADIUS_MOUNTAIN : SCORE_RADIUS_OUTDOOR;
        int minWallDist = usePackedGrid ? MIN_WALL_DIST_MOUNTAIN : MIN_WALL_DIST_OUTDOOR;
        LOG.ln("[FixingTheSyx] support pillars: terrain="
                + (isMountainTerrain ? "mountain" : "outdoor")
                + ", pasture=" + isPasture
                + ", pattern=" + (usePackedGrid ? "1x1-packed" : "+-cluster")
                + ", spacing=" + spacing
                + ", scoreRadius=" + scoreRadius
                + ", minWallDist=" + minWallDist);

        // 1. Collect all yellow tiles in the embryo.
        java.util.HashSet<Long> uncovered = new java.util.HashSet<>();
        for (int y = body.y1(); y <= body.y2(); y++) {
            for (int x = body.x1(); x <= body.x2(); x++) {
                if (!embryo.is(x, y)) continue;
                if (ConstructionData.dExpensive.is(x, y, 1)) {
                    uncovered.add(packKey(x, y));
                }
            }
        }
        if (uncovered.isEmpty()) return;

        // 2. Build grid points centred on the embryo's geometric centre. The grid extends
        //    far enough to cover the entire bounding box.
        int cx0 = (body.x1() + body.x2()) / 2;
        int cy0 = (body.y1() + body.y2()) / 2;
        int maxOffsetX = Math.max(cx0 - body.x1(), body.x2() - cx0) + spacing;
        int maxOffsetY = Math.max(cy0 - body.y1(), body.y2() - cy0) + spacing;
        int maxStepsX = maxOffsetX / spacing;
        int maxStepsY = maxOffsetY / spacing;

        java.util.ArrayList<int[]> gridPoints = new java.util.ArrayList<>();
        for (int sy = -maxStepsY; sy <= maxStepsY; sy++) {
            for (int sx = -maxStepsX; sx <= maxStepsX; sx++) {
                int cx = cx0 + sx * spacing;
                int cy = cy0 + sy * spacing;
                // Chebyshev ring distance from centre (drives outward ordering).
                int ring = Math.max(Math.abs(sx), Math.abs(sy));
                // Tie-break: process closer (|sy|, |sx|) first, then more negative sy/sx
                // so symmetric pairs stay adjacent in the placement order.
                gridPoints.add(new int[]{cx, cy, ring, Math.abs(sy), Math.abs(sx), sy, sx});
            }
        }

        gridPoints.sort((a, b) -> {
            if (a[2] != b[2]) return Integer.compare(a[2], b[2]); // smaller ring first
            if (a[3] != b[3]) return Integer.compare(a[3], b[3]);
            if (a[4] != b[4]) return Integer.compare(a[4], b[4]);
            if (a[5] != b[5]) return Integer.compare(a[5], b[5]);
            return Integer.compare(a[6], b[6]);
        });

        // 3. Walk the sorted grid; place a cluster at each valid grid slot that still
        //    covers an uncovered yellow. The cluster is validated AS A WHOLE — if even one
        //    arm is invalid, the entire cluster is skipped. The previous "partial cluster"
        //    behaviour (drop an arm that's outside the embryo) produced visibly asymmetric
        //    results for workshops/refiners with straight walls, and the player prefers a
        //    clean uniform grid over reaching the last yellow tile in irregular corners.
        int hardSafetyLimit = 500;
        for (int[] gp : gridPoints) {
            if (uncovered.isEmpty() || plannedPillars.size() >= hardSafetyLimit) break;
            int cx = gp[0], cy = gp[1];

            // Whole-cluster validation: every offset tile must be inside the embryo, not
            // a disconnect pinch-point, and far enough from the wall. minWallDist is
            // measured at the centre only — arms are 1 tile away from the centre so they
            // can sit on the wall edge without issue.
            if (!embryo.is(cx, cy)) continue;
            int wallDist = nearestWallDistance(embryo, cx, cy, MAX_WALL_DIST);
            if (wallDist < minWallDist) continue;
            boolean clusterValid = true;
            for (int[] off : pillarOffsets) {
                int tx = cx + off[0];
                int ty = cy + off[1];
                if (!embryo.is(tx, ty) || wouldDisconnect(embryo, tx, ty)) {
                    clusterValid = false;
                    break;
                }
            }
            if (!clusterValid) continue;
            if (countCoveredYellows(uncovered, cx, cy, scoreRadius) == 0) continue;

            plannedPillars.add(new int[]{cx, cy, wallDist * 1000});
            for (int dy = -scoreRadius; dy <= scoreRadius; dy++) {
                int ny = cy + dy;
                for (int dx = -scoreRadius; dx <= scoreRadius; dx++) {
                    int nx = cx + dx;
                    uncovered.remove(packKey(nx, ny));
                }
            }
        }
    }

    private int countCoveredYellows(java.util.HashSet<Long> uncovered, int cx, int cy, int radius) {
        int count = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            int ny = cy + dy;
            for (int dx = -radius; dx <= radius; dx++) {
                int nx = cx + dx;
                if (uncovered.contains(packKey(nx, ny))) count++;
            }
        }
        return count;
    }

    private static long packKey(int x, int y) {
        return ((long)(x + 0x4000)) << 32 | (long)(y + 0x4000);
    }

    /** True iff the player is currently constructing a room that uses the support system. */
    public static boolean isRoomEligible() {
        String s = structuralState();
        return "ACTIVE".equals(s);
    }

    /**
     * Decomposed structural eligibility check used by both gameplay and UI diagnostics.
     * Does NOT consider yellow tiles (use {@link #yellowsRemain(AREA)} for that). Returns:
     * {@code NO_PLACER}, {@code NO_AUTOWALLS}, {@code NO_BLUEPRINT}, {@code NOT_INDOOR_AREA},
     * or {@code ACTIVE} when the room is structurally eligible.
     */
    public static String structuralState() {
        Object placer = SupportReflect.roomPlacer();
        if (placer == null) return "NO_PLACER";
        if (!SupportReflect.autoWallsOn(placer)) return "NO_AUTOWALLS";
        try {
            RoomBlueprintImp blue = (RoomBlueprintImp) placer.getClass()
                    .getMethod("blueprint").invoke(placer);
            if (blue == null) return "NO_BLUEPRINT";
            if (!(blue.constructor().mustBeIndoors() && blue.constructor().usesArea()))
                return "NOT_INDOOR_AREA";
        } catch (Throwable t) {
            return "NO_BLUEPRINT";
        }
        return "ACTIVE";
    }

    /** True iff there is still at least one dExpensive==1 tile in the embryo. */
    public static boolean yellowsRemain(AREA embryo) {
        RECTANGLE body = embryo.body();
        for (int y = body.y1(); y <= body.y2(); y++) {
            for (int x = body.x1(); x <= body.x2(); x++) {
                if (!embryo.is(x, y)) continue;
                if (ConstructionData.dExpensive.is(x, y, 1)) return true;
            }
        }
        return false;
    }

    private void tryRequestFromHotkey() {
        if (!isRoomEligible()) return;
        Object placer = SupportReflect.roomPlacer();
        AREA embryo = SupportReflect.instanceOf(placer);
        if (embryo == null) return;
        GameWindow w = VIEW.s().getWindow();
        int tx = w.tile().x();
        int ty = w.tile().y();
        if (!SETT.IN_BOUNDS(tx, ty)) return;
        if (!embryo.is(tx, ty)) return; // hotkey must land on the embryo
        // Only trigger when the click target is itself a yellow ("weak support") tile.
        // Without this, Ctrl+Click anywhere inside the embryo (or on a tile the player is
        // trying to add to the embryo via vanilla expand) starts the auto-fill, which then
        // shrinks tiles concurrently with the player's intended placement — looking as if
        // "Ctrl+Click no longer builds on mountain". Yellow-only makes the gesture explicit:
        // aim at the problem tile to fix it.
        if (!ConstructionData.dExpensive.is(tx, ty, 1)) return;
        request();
    }

    /**
     * Places at most ONE pillar every {@link #PLACEMENT_PERIOD_TICKS} ticks. Vanilla
     * {@code UtilExtraCost.cache} recomputes the {@code dExpensive} map on tick
     * boundaries, AND vanilla worker plans / pathing need a beat to react to each
     * shrink — placing once per tick floods both and causes "Blocked by terrain!"
     * rejections on adjacent expansions plus NPEs in worker pathing.
     *
     * <p>Strategy: pop the next entry off the precomputed {@link #plannedPillars} list
     * (built once in {@link #request()} against the ORIGINAL embryo, so previously placed
     * pillars cannot contaminate the scoring map). Re-validate it against current state
     * (the player may have shrunk tiles manually, and earlier pillars may have already
     * cleared this candidate's yellows). When the plan is exhausted but yellows remain,
     * fall back to the legacy single-tile greedy path for narrow rooms / fragmented strips.
     */
    private void runFrame() {
        tickCounter++;
        if (tickCounter % PLACEMENT_PERIOD_TICKS != 0) return;

        Object placer = SupportReflect.roomPlacer();
        if (placer == null) { running = false; return; }
        AREA embryo = SupportReflect.instanceOf(placer);
        PLACABLE shrink = shrinkPlacableOf(placer);
        if (embryo == null || shrink == null) { running = false; return; }

        if (iterationsLeft <= 0) {
            LOG.ln("[FixingTheSyx] support pillars: hit iteration cap, partial fill");
            running = false;
            return;
        }

        // ---- Drain the precomputed plan ----
        // Place the WHOLE cluster every time — no per-arm validation here, because the
        // plan-time validation already guaranteed every arm sits inside the embryo and
        // wouldn't disconnect. Re-validating per arm would re-introduce the "asymmetric
        // cluster" bug where some "+" lose arms after earlier placements turn neighbouring
        // tiles into walls. Symmetry is more important to the player than mopping up the
        // last yellow tile, so we trust the plan.
        while (plannedIndex < plannedPillars.size()) {
            int[] pos = plannedPillars.get(plannedIndex++);
            int cx = pos[0], cy = pos[1];
            // Centre check only: if the player manually shrunk this tile after planning,
            // skip the whole cluster (don't partial-place).
            if (!embryo.is(cx, cy)) continue;

            int placed = 0;
            for (int[] off : pillarOffsets) {
                int tx = cx + off[0];
                int ty = cy + off[1];
                placePillar(shrink, tx, ty);
                placed++;
            }
            iterationsLeft--;
            LOG.ln("[FixingTheSyx] support pillars: placed planned cluster "
                    + plannedIndex + "/" + plannedPillars.size()
                    + " at (" + cx + "," + cy + ") size=" + placed
                    + ", cap left=" + iterationsLeft);
            return;
        }

        // ---- Plan exhausted: done ----
        // No single-tile greedy fallback. The fallback used to mop up yellows the grid
        // couldn't reach, but it placed lone pillars that broke the visual symmetry. The
        // player prefers a clean uniform grid with leftover yellows over an "everything
        // covered but scattered" look. They can clean up the few remaining yellows by hand.
        int remainingYellows = countYellows(embryo);
        if (remainingYellows == 0) {
            LOG.ln("[FixingTheSyx] support pillars: done");
        } else {
            LOG.ln("[FixingTheSyx] support pillars: done, " + remainingYellows
                    + " yellows left for the player to clean up by hand");
        }
        running = false;
    }

    /** Counts yellow tiles currently in the embryo. */
    private int countYellows(AREA embryo) {
        RECTANGLE body = embryo.body();
        int n = 0;
        for (int y = body.y1(); y <= body.y2(); y++) {
            for (int x = body.x1(); x <= body.x2(); x++) {
                if (!embryo.is(x, y)) continue;
                if (ConstructionData.dExpensive.is(x, y, 1)) n++;
            }
        }
        return n;
    }

    /**
     * Walks outwards in concentric square rings from (cx, cy) and returns the Chebyshev
     * distance to the FIRST tile that is not part of the embryo (i.e. a wall, mountain
     * face, or map edge). If no such tile is found within {@code maxRadius}, returns
     * {@code maxRadius}. Used to score how "central" a candidate sits inside the room.
     */
    private int nearestWallDistance(AREA embryo, int cx, int cy, int maxRadius) {
        // Centre tile itself: by contract we only call this when embryo.is(cx, cy), so
        // r=0 is always in-embryo. Start at r=1.
        for (int r = 1; r <= maxRadius; r++) {
            // Top and bottom edges of the square ring.
            for (int dx = -r; dx <= r; dx++) {
                if (!embryo.is(cx + dx, cy - r)) return r;
                if (!embryo.is(cx + dx, cy + r)) return r;
            }
            // Left and right edges (excluding corners already covered above).
            for (int dy = -r + 1; dy <= r - 1; dy++) {
                if (!embryo.is(cx - r, cy + dy)) return r;
                if (!embryo.is(cx + r, cy + dy)) return r;
            }
        }
        return maxRadius;
    }


    /** Cheap check: removing (cx, cy) cannot create an isolated single-tile sub-area. */
    private boolean wouldDisconnect(AREA embryo, int cx, int cy) {
        // Sufficient (not necessary) check: every ortho-neighbour that is in the embryo
        // must still be ortho-reachable to at least one OTHER embryo tile after removal.
        // For typical "thick" rooms this rejects pinch-point pillars.
        int neighbours = 0;
        if (embryo.is(cx + 1, cy)) neighbours++;
        if (embryo.is(cx - 1, cy)) neighbours++;
        if (embryo.is(cx, cy + 1)) neighbours++;
        if (embryo.is(cx, cy - 1)) neighbours++;
        // 0 neighbours = removing the only tile; 1 neighbour = pillar at the tip of a
        // 1-wide corridor (would orphan the rest). Reject both.
        return neighbours <= 1;
    }

    private void placePillar(PLACABLE shrink, int tx, int ty) {
        try {
            ((PlacableMulti) shrink).place(tx, ty, null, PLACER_TYPE.SQUARE);
        } catch (Throwable t) {
            LOG.ln("[FixingTheSyx] support pillars: place(" + tx + "," + ty + ") failed: " + t);
        }
    }

    /**
     * Reads the active RoomPlacer's blueprint via reflection. Returns null if the placer
     * isn't a vanilla RoomPlacer or the call throws — the caller treats null as "not a
     * pasture", which means it'll fall through to the "+" cluster path (safe default).
     */
    private static RoomBlueprintImp blueprintOf(Object roomPlacer) {
        if (roomPlacer == null) return null;
        try {
            return (RoomBlueprintImp) roomPlacer.getClass().getMethod("blueprint").invoke(roomPlacer);
        } catch (Throwable t) {
            return null;
        }
    }

    private static PLACABLE shrinkPlacableOf(Object roomPlacer) {
        PLACABLE area = SupportReflect.placerAreaOf(roomPlacer);
        if (area == null) return null;
        try {
            return area.getUndo();
        } catch (Throwable t) {
            return null;
        }
    }

}
