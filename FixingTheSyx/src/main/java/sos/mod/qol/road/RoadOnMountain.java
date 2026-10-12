package sos.mod.qol.road;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import init.constant.C;
import init.sprite.SPRITES;
import settlement.job.Job;
import settlement.job.JobBuildRoad;
import settlement.main.SETT;
import snake2d.CORE;
import snake2d.KEYCODES;
import snake2d.LOG;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.RECTANGLE;
import util.colors.GCOLOR;
import view.main.VIEW;
import view.subview.GameWindow;
import view.tool.PLACABLE;

/**
 * One-click road-on-mountain. While the road build tool is active and the player clicks on
 * a mountain tile, queue a tunnel job for that tile and remember it as "road-pending". Once
 * workers finish the tunnel (the tile is no longer mountain), the same road type is queued
 * automatically — so the player gets the same convenience the room placement system already
 * offers indoors.
 *
 * MVP scope: only the single tile under the cursor at click time. Drag-painting roads
 * across a mountain is not yet handled; each mountain tile needs its own click. A future
 * iteration could scan every tile a drag passes over.
 */
public final class RoadOnMountain {

    /** Tile index → road job to queue once the tunnel finishes. */
    private static final Map<Integer, JobBuildRoad> PENDING = new HashMap<>();
    /**
     * Mountain tiles seen under the road selection during the current drag. Tunnels are
     * only queued when the drag is CONFIRMED (left button released while still in the road
     * tool), never while the selection is still moving — Workshop reports: excavation orders
     * appeared as soon as the selection crossed a mountain, before the plan was confirmed.
     */
    private final java.util.Set<Integer> dragTiles = new java.util.HashSet<>();
    private JobBuildRoad dragRoad = null;

    private boolean prevLeftDown = false;
    /**
     * Per-launch toggle. Default ON so existing users keep the feature; players who don't
     * want auto-tunneling (urban layouts with stray mountain tiles, Dondorian houses, etc.)
     * can press the toggle hotkey to suppress it for the session. Not persisted — back to
     * ON on every game launch, since the rejection from "I forgot it was off" beats the
     * silent-side-effect from "I forgot it was on".
     */
    private boolean enabled = true;
    /** Rising-edge detect for the toggle hotkey. */
    private boolean prevToggleDown = false;
    private static Field PLACER_J_FIELD;
    private static Field PLACER_AREA_SELF_FIELD;
    private static boolean reflectionTried = false;

    public void tick() {
        if (VIEW.s() == null || !VIEW.s().isActive()) {
            prevLeftDown = false;
            prevToggleDown = false;
            return;
        }

        // Toggle hotkey: T flips the feature on/off, but ONLY while the road tool is active
        // — otherwise T would conflict with other tools/contexts that might bind it. We poll
        // the raw key state via CORE rather than vanilla's KEYS registry so the binding can
        // coexist with whatever the player has mapped in their settings. Edge-detect on
        // press so holding T doesn't spam-toggle.
        boolean toggleDown = activeRoadJob() != null
                && CORE.getInput().getKeyboard().isPressed(KEYCODES.KEY_T);
        if (toggleDown && !prevToggleDown) {
            enabled = !enabled;
            LOG.ln("[FixingTheSyx] road-on-mountain: toggled " + (enabled ? "ON" : "OFF"));
            // Drop any tunnel jobs we'd already promised to convert to roads — if the player
            // turned the feature off mid-build, they probably don't want stale promises
            // firing later.
            if (!enabled) PENDING.clear();
        }
        prevToggleDown = toggleDown;

        if (!enabled) {
            prevLeftDown = false;
            drainPending(); // keeps any in-flight pending honest if we get re-enabled
            return;
        }

        // Skip when the mouse is over any UI panel. Vanilla's ToolManager sets `hovered=true`
        // only when the cursor is over the game map (no interrupter/side panel consumed the
        // hover). Without this guard, ANY left click — opening the road-type picker, hitting
        // a settings button, clicking somewhere in another window — would also queue a tunnel
        // under the cursor's last map tile. Bug reported by Steam user "The Viral Divinity".
        if (!VIEW.s().tools.isHovered()) {
            prevLeftDown = false;
            return;
        }

        // While LEFT is held, vanilla repaints PlacerArea.self every frame with the current
        // selection — single tile under the cursor for SQUARE size 1, a rectangle for drag,
        // a flood for FILL. Iterate that area each tick and queue tunnels on every mountain
        // tile we see. We do NOT consume the click — vanilla still receives it, rejects the
        // mountain placements, and our tunnel work is purely additive.
        boolean leftDown = MButt.LEFT.isDown();
        if (leftDown) {
            JobBuildRoad road = activeRoadJob();
            if (road != null) {
                if (!prevLeftDown) { dragTiles.clear(); dragRoad = road; }
                // Remember what the selection covers; vanilla only commits the road on
                // release, so we commit the tunnels at the same moment.
                dragTiles.clear();
                AREA area = getPlacerArea();
                if (area != null && area.area() > 0) {
                    collectMountainTiles(area);
                } else {
                    GameWindow window = VIEW.s().getWindow();
                    int tx = window.tile().x(), ty = window.tile().y();
                    if (SETT.IN_BOUNDS(tx, ty) && SETT.TERRAIN().MOUNTAIN.is(tx, ty)) dragTiles.add(tileKey(tx, ty));
                }
            }
        } else if (prevLeftDown) {
            // Release = the player confirmed the plan (a cancelled drag — right click while
            // dragging — leaves the road tool, so activeRoadJob() is null and nothing is queued).
            JobBuildRoad road = activeRoadJob();
            if (road != null && dragRoad != null && !MButt.RIGHT.isDown()) {
                for (int key : dragTiles)
                    queueIfMountain(key % SETT.TWIDTH, key / SETT.TWIDTH, dragRoad);
            }
            dragTiles.clear();
            dragRoad = null;
        }

        prevLeftDown = leftDown;
        drainPending();
    }

    private void collectMountainTiles(AREA area) {
        RECTANGLE body = area.body();
        for (int y = body.y1(); y <= body.y2(); y++) {
            for (int x = body.x1(); x <= body.x2(); x++) {
                if (!area.is(x, y)) continue;
                if (SETT.IN_BOUNDS(x, y) && SETT.TERRAIN().MOUNTAIN.is(x, y)) dragTiles.add(tileKey(x, y));
            }
        }
    }

    private void queueIfMountain(int tx, int ty, JobBuildRoad road) {
        if (!SETT.IN_BOUNDS(tx, ty)) return;
        if (!SETT.TERRAIN().MOUNTAIN.is(tx, ty)) return;
        int key = tileKey(tx, ty);
        // Already pending AND the tunnel job still exists: nothing to do. If the player
        // removed the tunnel (smart delete) the pending entry must not block re-planning —
        // Workshop report: "after removing it, trying again says blocked".
        if (PENDING.containsKey(key) && SETT.JOBS().getter.get(tx, ty) != null) return;
        try {
            Job tunnel = SETT.JOBS().clearss.tunnel;
            tunnel.placer().place(tx, ty, null, null);
            PENDING.put(key, road);
            LOG.ln("[FixingTheSyx] road-on-mountain: queued tunnel + pending road at (" + tx + "," + ty + ")");
        } catch (Throwable t) {
            LOG.ln("[FixingTheSyx] road-on-mountain queue failed at (" + tx + "," + ty + "): " + t);
        }
    }

    /** Reflectively reach into view.tool.PlacerArea.self, the area vanilla paints each tick. */
    private AREA getPlacerArea() {
        try {
            if (PLACER_AREA_SELF_FIELD == null) {
                Class<?> cls = Class.forName("view.tool.PlacerArea");
                PLACER_AREA_SELF_FIELD = cls.getDeclaredField("self");
                PLACER_AREA_SELF_FIELD.setAccessible(true);
            }
            Object value = PLACER_AREA_SELF_FIELD.get(null);
            return value instanceof AREA ? (AREA) value : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Walk the pending list; for every tile that is no longer mountain (tunnel completed)
     * schedule the road there and drop it from the map.
     */
    private void drainPending() {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<Integer, JobBuildRoad>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, JobBuildRoad> e = it.next();
            int key = e.getKey();
            int tx = key % SETT.TWIDTH;
            int ty = key / SETT.TWIDTH;
            if (SETT.TERRAIN().MOUNTAIN.is(tx, ty)) {
                // Still mountain: keep waiting while the tunnel job exists; if the player
                // deleted the tunnel, forget the promise so the tile can be re-planned.
                if (SETT.JOBS().getter.get(tx, ty) == null) it.remove();
                continue;
            }
            try {
                JobBuildRoad road = e.getValue();
                road.placer().place(tx, ty, null, null);
                LOG.ln("[FixingTheSyx] road-on-mountain: tunnel done at (" + tx + "," + ty + "); queued road");
            } catch (Throwable t) {
                LOG.ln("[FixingTheSyx] road-on-mountain place road failed at (" + tx + "," + ty + "): " + t);
            }
            it.remove();
        }
    }

    /** Returns the active road build job, or null if the player is not in a road tool. */
    private JobBuildRoad activeRoadJob() {
        try {
            PLACABLE current = VIEW.s().tools.placer.getCurrent();
            if (current == null) return null;
            // The road's Placer (settlement.job.Placer) holds the Job in a private field 'j'.
            if (!reflectionTried) {
                reflectionTried = true;
                try {
                    Class<?> placerCls = Class.forName("settlement.job.Placer");
                    PLACER_J_FIELD = placerCls.getDeclaredField("j");
                    PLACER_J_FIELD.setAccessible(true);
                } catch (Throwable t) {
                    LOG.ln("[FixingTheSyx] road-on-mountain: cannot reach Placer.j: " + t);
                    return null;
                }
            }
            if (PLACER_J_FIELD == null) return null;
            // Only react if current's class IS settlement.job.Placer (or a subclass), so we
            // do not look at unrelated PlacableMulti instances.
            if (!PLACER_J_FIELD.getDeclaringClass().isInstance(current)) return null;
            Object j = PLACER_J_FIELD.get(current);
            return (j instanceof JobBuildRoad) ? (JobBuildRoad) j : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int tileKey(int x, int y) {
        return x + y * SETT.TWIDTH;
    }

    /**
     * Paint a green tile + tunnel icon over the red placement marker the road tool draws on
     * mountain tiles. Without this the player sees a red "blocked" preview and assumes the
     * click will fail; we want them to know the click queues a tunnel + road instead.
     */
    public void render(Renderer r, float ds) {
        if (VIEW.s() == null || !VIEW.s().isActive()) return;
        // If the toggle is off, suppress the green overlay so the player gets the standard
        // red vanilla preview — the "no green tile" itself signals the feature is off.
        if (!enabled) return;
        // Same guard as tick(): if the cursor is on a UI panel, suppress the green tunnel
        // preview so the player doesn't see a misleading "ready to tunnel" overlay floating
        // behind a popup window.
        if (!VIEW.s().tools.isHovered()) return;
        if (activeRoadJob() == null) return;
        GameWindow w = VIEW.s().getWindow();
        int tx = w.tile().x();
        int ty = w.tile().y();
        if (!SETT.IN_BOUNDS(tx, ty)) return;
        if (!SETT.TERRAIN().MOUNTAIN.is(tx, ty)) return;

        int x = w.tile().rel().x();
        int y = w.tile().rel().y();
        // Solid OK-green rect masks the vanilla red preview underneath.
        GCOLOR.MAP().OK.render(r, x, x + C.TILE_SIZE, y, y + C.TILE_SIZE);
        // Small tunnel/anti icon to communicate "will tunnel".
        try {
            SPRITES.icons().m.anti.render(r, x, y);
        } catch (Throwable ignore) { /* best-effort */ }
        COLOR.unbind();
    }
}
