package planahead;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import game.faction.FACTIONS;
import settlement.room.main.RoomBlueprintImp;
import snake2d.LOG;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.misc.ACTION;
import view.keyboard.KEYS;
import view.main.VIEW;

/**
 * Patches the build-menu BRoom buttons so tech-locked rooms remain clickable
 * and route through a click handler that initiates placement directly. The
 * actual auto-Plan-Mode handling lives in {@link PlanModeWatch}; this class
 * is only responsible for keeping the buttons usable.
 *
 * Strategy: reflection on view.sett.ui.bottom.UIBuildPanel.main (BuildMain),
 * then BuildMain.map (KeyMap of BParenter), filtering for BRoom inner
 * instances, then replacing each BRoom's clickAction and per-tick forcing
 * its inherited isActive field to true so CLICKABLE.click() will fire.
 */
final class BuildMenuPatch {

    private final List<Patched> patched = new ArrayList<>();
    private Object lastBuildMain;
    private Class<?> bRoomClass;
    private Field bRoomRoomField;
    private Field clickableIsActive;
    private Field clickableClickAction;
    private Field uiBuildPanelMain;
    private Field buildMainMap;
    private Field buildMainInter;
    private Method keyMapAll;
    private Method interHide;
    private boolean bootFailed;

    void tick() {
        if (bootFailed) return;
        if (VIEW.s() == null) return;

        try {
            ensureReflection();
        } catch (Throwable t) {
            bootFailed = true;
            LOG.ln("[PlanAhead] reflection bootstrap failed: " + t);
            return;
        }

        Object uiBuild = VIEW.s().ui.bottom;
        if (uiBuild == null) return;

        Object currentMain;
        try {
            currentMain = uiBuildPanelMain.get(uiBuild);
        } catch (IllegalAccessException e) {
            return;
        }
        if (currentMain == null) return;

        if (currentMain != lastBuildMain) {
            rebuildCache(currentMain);
            lastBuildMain = currentMain;
        }

        for (Patched p : patched) {
            boolean unlocked = p.room.reqs.passes(FACTIONS.player());
            if (unlocked) continue;
            try {
                clickableIsActive.setBoolean(p.button, true);
            } catch (IllegalAccessException ignored) { }
        }
    }

    private void ensureReflection() throws Exception {
        if (uiBuildPanelMain != null) return;

        Class<?> uiBuildClass = Class.forName("view.sett.ui.bottom.UIBuildPanel");
        uiBuildPanelMain = uiBuildClass.getDeclaredField("main");
        uiBuildPanelMain.setAccessible(true);

        Class<?> buildMainClass = Class.forName("view.sett.ui.bottom.BuildMain");
        buildMainMap = buildMainClass.getDeclaredField("map");
        buildMainMap.setAccessible(true);
        buildMainInter = buildMainClass.getDeclaredField("inter");
        buildMainInter.setAccessible(true);

        bRoomClass = Class.forName("view.sett.ui.bottom.BuildMain$BRoom");
        bRoomRoomField = bRoomClass.getDeclaredField("room");
        bRoomRoomField.setAccessible(true);

        Class<?> abs = Class.forName("snake2d.util.gui.clickable.CLICKABLE$ClickableAbs");
        clickableIsActive = abs.getDeclaredField("isActive");
        clickableIsActive.setAccessible(true);
        clickableClickAction = abs.getDeclaredField("clickAction");
        clickableClickAction.setAccessible(true);

        Class<?> interClass = Class.forName("view.sett.ui.bottom.Inter");
        interHide = interClass.getDeclaredMethod("hide");
        interHide.setAccessible(true);

        Class<?> keyMapClass = Class.forName("snake2d.util.sets.KeyMap");
        keyMapAll = keyMapClass.getDeclaredMethod("all");
        keyMapAll.setAccessible(true);
    }

    private void rebuildCache(Object buildMain) {
        patched.clear();
        Object map;
        Object inter;
        try {
            map = buildMainMap.get(buildMain);
            inter = buildMainInter.get(buildMain);
        } catch (IllegalAccessException e) {
            return;
        }
        if (map == null) return;

        // KeyMap.all() returns a LIST<T>; iterate that.
        try {
            Object listObj = keyMapAll.invoke(map);
            if (!(listObj instanceof Iterable)) return;
            for (Object value : (Iterable<?>) listObj) {
                if (value == null) continue;
                if (!bRoomClass.isInstance(value)) continue;
                CLICKABLE button = (CLICKABLE) value;
                RoomBlueprintImp room = (RoomBlueprintImp) bRoomRoomField.get(value);
                ACTION original = (ACTION) clickableClickAction.get(value);
                Patched p = new Patched(button, room, original, inter);
                button.clickActionSet(p::run);
                patched.add(p);
            }
        } catch (Throwable t) {
            LOG.ln("[PlanAhead] cache rebuild failed: " + t);
        }
    }

    private final class Patched {
        final CLICKABLE button;
        final RoomBlueprintImp room;
        final ACTION original;
        final Object inter;

        Patched(CLICKABLE button, RoomBlueprintImp room, ACTION original, Object inter) {
            this.button = button;
            this.room = room;
            this.original = original;
            this.inter = inter;
        }

        void run() {
            boolean unlocked = room.reqs.passes(FACTIONS.player());
            if (unlocked) {
                if (original != null) original.exe();
                return;
            }
            // Tech-locked path: replicate the vanilla BRoom click body but
            // skip the early-return-on-locked guard. Preserving the
            // Ctrl+click → saved-prints branch keeps Better Blueprints (and
            // the vanilla blueprint book) functional on tech-locked entries:
            // the saved layout drops in normally, and any tech-locked
            // constructions it produces are caught by the watchdog and
            // forced dormant on the next tick.
            try {
                if (inter != null) interHide.invoke(inter);
            } catch (Throwable ignored) { }
            try {
                if (KEYS.MAIN().MOD.isPressed() && VIEW.s().ui.prints.has(room)) {
                    VIEW.s().ui.prints.open(room);
                } else {
                    VIEW.s().ui.placer.init(room, -1, -1);
                }
            } catch (Throwable t) {
                LOG.ln("[PlanAhead] placement init failed: " + t);
            }
        }
    }
}
