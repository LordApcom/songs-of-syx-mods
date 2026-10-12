package sos.mod.qol.support;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import settlement.main.SETT;
import settlement.room.main.placement.PLACEMENT;
import snake2d.LOG;
import snake2d.util.datatypes.AREA;
import view.tool.PLACABLE;

/**
 * Centralised reflective access to the vanilla classes used by the support-pillar feature.
 * Each accessor caches its Field/Method lookup once. Failures are logged and surface as
 * null returns, so callers can degrade gracefully rather than crashing.
 */
public final class SupportReflect {

    private static Field ROOM_PLACER_placerArea;
    private static Field ROOM_PLACER_instance;
    private static Field ROOM_PLACER_autoWalls;
    private static boolean tried = false;

    private SupportReflect() {}

    static synchronized void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> rp = Class.forName("settlement.room.main.placement.RoomPlacer");
            ROOM_PLACER_placerArea = rp.getDeclaredField("placerArea");
            ROOM_PLACER_placerArea.setAccessible(true);
            ROOM_PLACER_instance = rp.getDeclaredField("instance");
            ROOM_PLACER_instance.setAccessible(true);
            ROOM_PLACER_autoWalls = rp.getDeclaredField("autoWalls");
            ROOM_PLACER_autoWalls.setAccessible(true);
        } catch (Throwable t) {
            LOG.ln("[FixingTheSyx] support: cannot reach RoomPlacer fields: " + t);
        }
    }

    /** Returns the active RoomPlacer (the embryo of the room being placed), or null. */
    public static Object roomPlacer() {
        try {
            PLACEMENT placement = SETT.ROOMS().placement;
            Field f = placement.getClass().getDeclaredField("placer");
            f.setAccessible(true);
            return f.get(placement);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Returns the {@code placerArea} PLACABLE of the active RoomPlacer (used to shrink). */
    static PLACABLE placerAreaOf(Object roomPlacer) {
        init();
        if (ROOM_PLACER_placerArea == null || roomPlacer == null) return null;
        try {
            return (PLACABLE) ROOM_PLACER_placerArea.get(roomPlacer);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Returns the {@code instance} (embryo AREA) of the active RoomPlacer. */
    public static AREA instanceOf(Object roomPlacer) {
        init();
        if (ROOM_PLACER_instance == null || roomPlacer == null) return null;
        try {
            return (AREA) ROOM_PLACER_instance.get(roomPlacer);
        } catch (Throwable t) {
            return null;
        }
    }

    /** True if autoWalls is enabled on the placer. Pillars only become walls when on. */
    static boolean autoWallsOn(Object roomPlacer) {
        init();
        if (ROOM_PLACER_autoWalls == null || roomPlacer == null) return false;
        try {
            Object autoWalls = ROOM_PLACER_autoWalls.get(roomPlacer);
            Method m = autoWalls.getClass().getMethod("is");
            return (boolean) m.invoke(autoWalls);
        } catch (Throwable t) {
            return false;
        }
    }
}
