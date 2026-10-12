package sos.mod.qol.fish;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import settlement.main.SETT;
import settlement.room.food.fish.ROOM_FISHERY;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.placement.PLACEMENT;
import snake2d.LOG;
import snake2d.MButt;
import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.DIR;
import view.keyboard.KEYS;
import view.main.VIEW;
import view.subview.GameWindow;
import view.tool.PLACABLE;
import view.tool.PLACER_TYPE;
import view.tool.PlacableMulti;

/**
 * While the Fishery construction tool is active, intercepts Ctrl+Click on a shallow water
 * tile and flood-fills the connected river region into the active room area embryo. The
 * player still sees the standard fishery UI and can confirm or cancel after the flood.
 *
 * MVP: fills a single fishery worth of contiguous shallow water bounded by
 * {@link Room#MAX_SIZE} and {@link Room#MAX_DIM}. Repeat the gesture on a different river
 * stretch to draw additional fisheries.
 */
public final class FisheryAutoFill {

    private boolean prevLeftDown = false;
    private static AREA placerAreaSelf = null;
    private static boolean placerAreaSelfTried = false;

    public void tick() {
        if (VIEW.s() == null || !VIEW.s().isActive()) {
            prevLeftDown = false;
            return;
        }

        boolean leftDown = MButt.LEFT.isDown();
        boolean justClicked = leftDown && !prevLeftDown;
        prevLeftDown = leftDown;

        if (!justClicked) return;
        if (!KEYS.MAIN().MOD.isPressed()) return; // Ctrl modifier required

        PLACABLE active = VIEW.s().tools.placer.getCurrent();
        if (!(active instanceof PlacableMulti)) {
            return;
        }
        if (!isFisheryPlacement()) {
            return;
        }

        GameWindow window = VIEW.s().getWindow();
        int tx = window.tile().x();
        int ty = window.tile().y();
        if (!SETT.IN_BOUNDS(tx, ty)) return;
        if (!SETT.TERRAIN().WATER.SHALLOW.is(tx, ty)) {
            LOG.ln("[QualityOfLife] fishery auto-fill: tile (" + tx + "," + ty + ") is not shallow water; aborting");
            return;
        }

        try {
            int placed = floodFillShallow((PlacableMulti) active, tx, ty);
            LOG.ln("[QualityOfLife] fishery auto-fill: added " + placed + " tiles starting at (" + tx + "," + ty + ")");
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] fishery auto-fill failed: " + t);
            t.printStackTrace();
        }
    }

    /**
     * Three-pass shore fill that produces a 4-connected band along the deep edge:
     *   1. Discover the connected component of shallow water reachable from (sx, sy).
     *   2. Within the component, mark "shore" tiles — those ortho-adjacent to deep water
     *      or a bridge — and "near-shore" tiles — shallow tiles ortho-adjacent to a shore.
     *      Together this is a 2-tile band that bridges the gaps caused by diagonal shore
     *      curvature so the room area is ortho-connected (vanilla refuses to confirm a
     *      disconnected room).
     *   3. BFS from the clicked tile through the keep-set placing tiles one by one, capped
     *      by {@link Room#MAX_SIZE} and {@link Room#MAX_DIM} so the resulting room is legal.
     */
    private int floodFillShallow(PlacableMulti placer, int sx, int sy) {
        AREA areaArg = resolvePlacerAreaSelf();

        // --- Pass 1: gather the connected shallow component ---
        Set<Integer> component = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sy});
        component.add(tileKey(sx, sy));
        while (!queue.isEmpty()) {
            int[] node = queue.poll();
            int x = node[0], y = node[1];
            for (int i = 0; i < DIR.ORTHO.size(); i++) {
                DIR d = DIR.ORTHO.get(i);
                int nx = x + d.x(), ny = y + d.y();
                if (!SETT.IN_BOUNDS(nx, ny)) continue;
                int key = tileKey(nx, ny);
                if (component.contains(key)) continue;
                if (!SETT.TERRAIN().WATER.SHALLOW.is(nx, ny)) continue;
                if (SETT.ROOMS().map.is(nx, ny)) continue;
                component.add(key);
                queue.add(new int[]{nx, ny});
            }
        }

        // --- Pass 2: keep the ENTIRE connected shallow component ---
        // Previously this narrowed `keep` to a 2-tile band hugging deep water
        // or bridges, so the auto-fill only ever covered the deep-sea shoreline
        // and ignored the rest of a lake/inlet — the "only deep-sea tiles"
        // Workshop complaint. A fishery only needs shallow water (vanilla
        // Constructor blocks placement on DEEP, not shallow), so every tile in
        // the connected shallow flood is a valid candidate. Pass 3 still
        // validates each tile with the game's own placer.isPlacable and caps
        // the result at Room.MAX_SIZE/MAX_DIM, so this stays legal.
        Set<Integer> keep = component;
        if (keep.isEmpty()) return 0;

        // --- Pass 3: BFS-place from the click point through keep, respecting room bounds ---
        Deque<int[]> placeQueue = new ArrayDeque<>();
        Set<Integer> placedSet = new HashSet<>();
        int startKey = tileKey(sx, sy);
        if (keep.contains(startKey)) {
            placeQueue.add(new int[]{sx, sy});
        } else {
            // Click was on inland shallow — start from any keep tile (best-effort).
            Integer any = keep.iterator().next();
            placeQueue.add(new int[]{any % SETT.TWIDTH, any / SETT.TWIDTH});
        }

        // The room legality limits (Room.MAX_SIZE / MAX_DIM) apply to the WHOLE embryo:
        // tiles the player already painted (or a previous Ctrl+Click added) plus what we
        // add now. Vanilla's PlacerArea.isPlacable only re-reads the existing embryo on
        // the first tile of a drag, so calling it from here checked against stale data
        // and the fill could grow the room past the dimension limit (Workshop report:
        // "Ctrl+Click ignores the max dimension message"). So we track the bounds ourselves
        // from the real embryo and use only vanilla's terrain/room checks per tile.
        Room embryo = resolveEmbryo();
        RoomBlueprintImp bp = embryo != null && embryo.blueprint() instanceof RoomBlueprintImp
                ? (RoomBlueprintImp) embryo.blueprint() : null;
        int existing = 0;
        int minX = 0, maxX = 0, minY = 0, maxY = 0;
        boolean firstPlaced = false;
        if (embryo instanceof AREA) {
            AREA ea = (AREA) embryo;
            for (snake2d.util.datatypes.COORDINATE c : ea.body()) {
                if (!ea.is(c.x(), c.y())) continue;
                if (!firstPlaced) { minX = maxX = c.x(); minY = maxY = c.y(); firstPlaced = true; }
                minX = Math.min(minX, c.x()); maxX = Math.max(maxX, c.x());
                minY = Math.min(minY, c.y()); maxY = Math.max(maxY, c.y());
                existing++;
            }
        }

        // Vanilla tool order: finishChecking resets the shared coordinate stack (GUTIL.coos,
        // capacity MAX_SIZE+1, wraps around when exceeded) that place() pushes every tile
        // onto; finishPlacing drains it into the embryo history + item validation. Skipping
        // them left the stack un-drained (no undo history, wrapped/corrupt entries after two
        // big fills) — we now bracket the fill exactly like the vanilla tool does.
        try { placer.finishChecking(areaArg); } catch (Throwable t) { /* defensive */ }

        int placed = 0;
        int skippedDim = 0;
        try {
            while (!placeQueue.isEmpty()) {
                if (existing + placed + 1 >= Room.MAX_SIZE) break;
                int[] node = placeQueue.poll();
                int x = node[0], y = node[1];
                if (!SETT.IN_BOUNDS(x, y)) continue;
                int key = tileKey(x, y);
                if (!keep.contains(key)) continue;
                if (placedSet.contains(key)) continue;

                int nMinX = firstPlaced ? Math.min(minX, x) : x;
                int nMaxX = firstPlaced ? Math.max(maxX, x) : x;
                int nMinY = firstPlaced ? Math.min(minY, y) : y;
                int nMaxY = firstPlaced ? Math.max(maxY, y) : y;
                if ((nMaxX - nMinX + 1) > Room.MAX_DIM || (nMaxY - nMinY + 1) > Room.MAX_DIM) {
                    skippedDim++;
                    continue;
                }

                CharSequence err = null;
                try {
                    err = PLACEMENT.placable(x, y, bp, false);
                    if (err == null && bp != null)
                        err = bp.constructor().placable(x, y, null, null);
                } catch (Throwable t) { err = "check failed"; }
                if (err != null && err.length() > 0) continue;

                try {
                    placer.place(x, y, areaArg, PLACER_TYPE.FILL);
                } catch (Throwable t) {
                    LOG.ln("[QualityOfLife] fishery auto-fill place() failed at (" + x + "," + y + "): " + t);
                    continue;
                }
                placedSet.add(key);
                placed++;
                minX = nMinX; maxX = nMaxX; minY = nMinY; maxY = nMaxY;
                firstPlaced = true;

                for (int i = 0; i < DIR.ORTHO.size(); i++) {
                    DIR d = DIR.ORTHO.get(i);
                    int nx = x + d.x(), ny = y + d.y();
                    if (!SETT.IN_BOUNDS(nx, ny)) continue; // tileKey would wrap to the other map edge
                    int nkey = tileKey(nx, ny);
                    if (keep.contains(nkey) && !placedSet.contains(nkey)) {
                        placeQueue.add(new int[]{nx, ny});
                    }
                }
            }
        } finally {
            try { placer.finishPlacing(areaArg); } catch (Throwable t) { LOG.ln("[QualityOfLife] finishPlacing failed: " + t); }
        }
        if (skippedDim > 0)
            LOG.ln("[QualityOfLife] fishery auto-fill: stopped at the room dimension limit (" + Room.MAX_DIM + "), " + skippedDim + " tile(s) left out");

        return placed;
    }

    /** The room embryo being drawn (RoomPlacer.instance, package-private) via reflection. */
    private static Room resolveEmbryo() {
        try {
            Object placement = SETT.ROOMS().placement;
            Field placerField = placement.getClass().getDeclaredField("placer");
            placerField.setAccessible(true);
            Object placer = placerField.get(placement);
            Field insField = placer.getClass().getDeclaredField("instance");
            insField.setAccessible(true);
            Object ins = insField.get(placer);
            return ins instanceof Room ? (Room) ins : null;
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] could not resolve room embryo: " + t);
            return null;
        }
    }

    /** True if any ortho-neighbor of (x,y) is deep water or a bridge. */
    private static boolean hasDeepOrBridgeOrtho(int x, int y) {
        for (int i = 0; i < DIR.ORTHO.size(); i++) {
            DIR d = DIR.ORTHO.get(i);
            int nx = x + d.x();
            int ny = y + d.y();
            if (!SETT.IN_BOUNDS(nx, ny)) continue;
            if (SETT.TERRAIN().WATER.DEEP.is(nx, ny)) return true;
            if (SETT.TERRAIN().WATER.BRIDGE.is(nx, ny)) return true;
        }
        return false;
    }

    private static int tileKey(int x, int y) {
        return x + y * SETT.TWIDTH;
    }

    /** Detects whether the active room placement is for the fishery blueprint. */
    private boolean isFisheryPlacement() {
        try {
            Object placement = SETT.ROOMS().placement;
            Field placerField = placement.getClass().getDeclaredField("placer");
            placerField.setAccessible(true);
            Object placer = placerField.get(placement);
            Object blueprint = placer.getClass().getMethod("blueprint").invoke(placer);
            return blueprint instanceof ROOM_FISHERY;
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] fishery detection failed: " + t);
            return false;
        }
    }

    /**
     * The vanilla {@code PlacerArea.self} is the canonical AREA passed by
     * {@code PlacableMultiTool.place()}; reuse it so PlacerArea.isPlacable does not NPE on a
     * null AREA argument.
     */
    private static AREA resolvePlacerAreaSelf() {
        if (placerAreaSelf != null || placerAreaSelfTried) return placerAreaSelf;
        placerAreaSelfTried = true;
        try {
            Class<?> cls = Class.forName("settlement.room.main.placement.PlacerArea");
            Field self = cls.getDeclaredField("self");
            self.setAccessible(true);
            Object value = self.get(null);
            if (value instanceof AREA) placerAreaSelf = (AREA) value;
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] could not resolve PlacerArea.self: " + t);
        }
        return placerAreaSelf;
    }
}
