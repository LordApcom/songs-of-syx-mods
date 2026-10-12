package view.menu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;

import game.save.SaveFile;
import init.paths.PATH;
import init.paths.PATHS;
import snake2d.LOG;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.RECTANGLE;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import view.keyboard.KEYS;
import view.main.VIEW;

/**
 * Same-package patcher that adds Ctrl+Click multi-select + bulk delete to the two save-list
 * widgets the game ships with: {@link MenuScreenLoad} (used by both the main-menu Load
 * screen and {@link IMenuLoad} in-game) and {@link IMenuSave} (in-game save panel).
 *
 * Strategy without hijacking any UI button:
 * <ol>
 *   <li>Each tick, walk the menu UI tree looking for any visible save list. Attach a per-
 *       instance {@code State} the first time we see it.</li>
 *   <li>Sync our {@code Set<String>} of selected file names with the widget's private
 *       {@code int selectedSave}: when it changes and Ctrl is held, toggle the new entry;
 *       otherwise replace the set with the single click target.</li>
 *   <li>Render a yellow overlay over every Savebutt whose name is in the set.</li>
 *   <li>Mass-delete by riding the vanilla single-delete: as soon as the {@code saves}
 *       array shrinks (the vanilla Yes button just deleted one), figure out which name
 *       disappeared, and if our set still contains more files, delete each of them
 *       directly via {@link PATH#delete(CharSequence)} and refresh the list. The vanilla
 *       confirmation prompt that the user already accepted serves as the "are you sure?"
 *       for the whole batch.</li>
 * </ol>
 *
 * Limitation: this patch needs the SCRIPT lifecycle to be running, which only starts after
 * the player has loaded a save once; the very first appearance of the main-menu Load
 * screen (before any session) still uses vanilla single-select.
 */
public final class QolSaveMultiSelect {

    private static final WeakHashMap<Object, State> STATES = new WeakHashMap<>();
    private static boolean reflectionReady = false;

    // MenuScreenLoad accessors
    private static Field MSL_selectedSave;
    private static Field MSL_saves;
    private static Field MSL_path;
    private static Field MSL_main;
    private static Field MSL_current;
    private static Method MSL_populateSaves;

    // IMenuSave accessors
    private static Field IMS_selectedSave;
    private static Field IMS_saves;
    private static Method IMS_populateSaves;

    // GuiSection child enumeration
    private static Field GS_renderables;

    private QolSaveMultiSelect() {}

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            MSL_selectedSave = MenuScreenLoad.class.getDeclaredField("selectedSave");
            MSL_selectedSave.setAccessible(true);
            MSL_saves = MenuScreenLoad.class.getDeclaredField("saves");
            MSL_saves.setAccessible(true);
            MSL_path = MenuScreenLoad.class.getDeclaredField("path");
            MSL_path.setAccessible(true);
            MSL_main = MenuScreenLoad.class.getDeclaredField("main");
            MSL_main.setAccessible(true);
            MSL_current = MenuScreenLoad.class.getDeclaredField("current");
            MSL_current.setAccessible(true);
            MSL_populateSaves = MenuScreenLoad.class.getDeclaredMethod("populateSaves");
            MSL_populateSaves.setAccessible(true);

            IMS_selectedSave = IMenuSave.class.getDeclaredField("selectedSave");
            IMS_selectedSave.setAccessible(true);
            IMS_saves = IMenuSave.class.getDeclaredField("saves");
            IMS_saves.setAccessible(true);
            IMS_populateSaves = IMenuSave.class.getDeclaredMethod("populateSaves");
            IMS_populateSaves.setAccessible(true);

            GS_renderables = GuiSection.class.getDeclaredField("renderables");
            GS_renderables.setAccessible(true);

            reflectionReady = true;
        } catch (Throwable e) {
            LOG.ln("[FixingTheSyx] save multi-select: reflection init failed: " + e);
        }
    }

    private static int loggedTickCount = 0;
    private static int lastWidgetCountLogged = -1;

    public static void tick() {
        initReflection();
        if (!reflectionReady) return;

        Set<Object> widgets = findActiveWidgets();
        if (widgets.size() != lastWidgetCountLogged) {
            lastWidgetCountLogged = widgets.size();
            LOG.ln("[FixingTheSyx] save multi-select: tracking " + widgets.size() + " widget(s)");
        }
        for (Object widget : widgets) {
            State st = STATES.get(widget);
            if (st == null) {
                st = new State();
                STATES.put(widget, st);
                LOG.ln("[FixingTheSyx] save multi-select: attached to " + widget.getClass().getSimpleName());
            }
            try {
                if (widget instanceof MenuScreenLoad) {
                    syncMSL((MenuScreenLoad) widget, st);
                } else if (widget instanceof IMenuSave) {
                    syncIMS((IMenuSave) widget, st);
                }
            } catch (Throwable t) {
                if (loggedTickCount++ < 5) LOG.ln("[FixingTheSyx] save multi-select sync failed: " + t);
            }
        }
    }

    public static void renderOverlay(SPRITE_RENDERER r) {
        if (!reflectionReady) return;
        for (java.util.Map.Entry<Object, State> e : STATES.entrySet()) {
            Object widget = e.getKey();
            State st = e.getValue();
            if (st.selectedNames.size() <= 1) continue;
            try {
                GuiSection root = rootOf(widget);
                if (root == null) continue;
                if (widget instanceof MenuScreenLoad) {
                    Object cur = MSL_current.get(widget);
                    Object main = MSL_main.get(widget);
                    if (cur != main) continue;
                }
                highlightSavebutts(r, root, widget, st.selectedNames, 0);
            } catch (Throwable t) { /* swallow */ }
        }
    }

    private static GuiSection rootOf(Object widget) throws IllegalAccessException {
        if (widget instanceof MenuScreenLoad) return (GuiSection) MSL_main.get(widget);
        if (widget instanceof IMenuSave) return (GuiSection) widget;
        return null;
    }

    /* ------------------------------------------------------------------ MSL */

    private static void syncMSL(MenuScreenLoad msl, State st) throws Throwable {
        SaveFile[] saves = (SaveFile[]) MSL_saves.get(msl);
        int sel = (int) MSL_selectedSave.get(msl);
        applySelectionChange(saves, sel, st);
        boolean shrank = detectShrink(saves, st);
        if (shrank) {
            massDeleteRemaining(saves, st, (PATH) MSL_path.get(msl));
            try {
                MSL_populateSaves.invoke(msl);
                MSL_selectedSave.set(msl, -1);
                st.lastSeen = -1;
            } catch (Throwable t) { /* swallow */ }
        }
        st.prevNames = collectNames(saves);
    }

    /* ------------------------------------------------------------------ IMS */

    private static void syncIMS(IMenuSave ims, State st) throws Throwable {
        SaveFile[] saves = (SaveFile[]) IMS_saves.get(ims);
        int sel = (int) IMS_selectedSave.get(ims);
        applySelectionChange(saves, sel, st);
        boolean shrank = detectShrink(saves, st);
        if (shrank) {
            massDeleteRemaining(saves, st, PATHS.local().save());
            try {
                IMS_populateSaves.invoke(ims);
                IMS_selectedSave.set(ims, -1);
                st.lastSeen = -1;
            } catch (Throwable t) { /* swallow */ }
        }
        st.prevNames = collectNames(saves);
    }

    /* --------------------------------------------------------------- shared */

    private static void applySelectionChange(SaveFile[] saves, int sel, State st) {
        if (sel == st.lastSeen) return;
        int prevSeen = st.lastSeen;
        st.lastSeen = sel;

        if (sel < 0) {
            st.selectedNames.clear();
            return;
        }
        if (sel >= saves.length) return;
        String name = saves[sel].fullName.toString();
        boolean ctrl = KEYS.MAIN().MOD.isPressed();
        if (ctrl) {
            if (!st.selectedNames.add(name)) st.selectedNames.remove(name);
        } else {
            st.selectedNames.clear();
            st.selectedNames.add(name);
        }
        LOG.ln("[FixingTheSyx] save multi-select: sel " + prevSeen + "->" + sel
                + " ctrl=" + ctrl + " set.size=" + st.selectedNames.size());
    }

    /** True when {@code saves} got shorter than the snapshot we took last tick. */
    private static boolean detectShrink(SaveFile[] saves, State st) {
        if (st.prevNames == null) return false;
        if (saves.length >= st.prevNames.length) return false;
        // Remove any names that disappeared (vanilla just deleted them) from our set.
        Set<String> current = new HashSet<>();
        for (SaveFile s : saves) current.add(s.fullName.toString());
        for (String prev : st.prevNames) {
            if (!current.contains(prev)) st.selectedNames.remove(prev);
        }
        return true;
    }

    /**
     * After vanilla deleted one save, delete every other name still in our set directly
     * via the file API. The vanilla confirmation the user just accepted counts for the
     * whole batch.
     */
    private static void massDeleteRemaining(SaveFile[] saves, State st, PATH path) {
        if (st.selectedNames.isEmpty()) return;
        for (String name : new LinkedHashSet<>(st.selectedNames)) {
            try {
                path.delete(name);
            } catch (Throwable t) {
                LOG.ln("[FixingTheSyx] mass-delete failed on " + name + ": " + t);
            }
        }
        st.selectedNames.clear();
    }

    private static String[] collectNames(SaveFile[] saves) {
        String[] out = new String[saves.length];
        for (int i = 0; i < saves.length; i++) out[i] = saves[i].fullName.toString();
        return out;
    }

    /* ------------------------------------------------------------ discovery */

    private static Set<Object> findActiveWidgets() {
        Set<Object> result = new HashSet<>();
        try {
            Object inters = VIEW.inters();
            if (inters != null) {
                Field menuF = inters.getClass().getField("menu");
                Object iMenu = menuF.get(inters);
                walk(iMenu, result, 0);
            }
        } catch (Throwable t) { /* swallow */ }
        return result;
    }

    private static void walk(Object node, Set<Object> out, int depth) {
        if (node == null || depth > 6) return;
        if (node instanceof MenuScreenLoad) { out.add(node); return; }
        if (node instanceof IMenuSave) { out.add(node); /* and continue, MSL may be nested too */ }
        for (Class<?> c = node.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                Class<?> t = f.getType();
                if (t.isPrimitive() || t.isArray()) continue;
                if (t.getName().startsWith("java.")) continue;
                // GuiSection lives in snake2d.util.gui — do NOT skip those, they hold our targets.
                try {
                    f.setAccessible(true);
                    Object child = f.get(node);
                    if (child == null) continue;
                    if (child instanceof MenuScreenLoad || child instanceof IMenuSave) {
                        walk(child, out, depth + 1);
                    } else if (child instanceof IMenu
                            || child instanceof IMenuLoad
                            || child instanceof GuiSection) {
                        walk(child, out, depth + 1);
                    }
                } catch (Throwable t2) { /* swallow */ }
            }
        }
    }

    /* -------------------------------------------------------------- overlay */

    private static void highlightSavebutts(SPRITE_RENDERER r, RENDEROBJ node, Object widget,
                                           Set<String> selected, int depth) {
        if (node == null || depth > 8) return;

        Class<?> cls = node.getClass();
        Class<?> enc = cls.getEnclosingClass();
        boolean isMSLBtn = enc == MenuScreenLoad.class && cls.getSimpleName().equals("Savebutt");
        boolean isIMSBtn = enc == IMenuSave.class && cls.getSimpleName().equals("SaveEntry");
        if (isMSLBtn || isIMSBtn) {
            try {
                Field idxF = findField(cls, "index");
                if (idxF == null) return;
                idxF.setAccessible(true);
                int idx = (int) idxF.get(node);
                SaveFile[] saves = widget instanceof MenuScreenLoad
                        ? (SaveFile[]) MSL_saves.get(widget)
                        : (SaveFile[]) IMS_saves.get(widget);
                if (idx < 0 || idx >= saves.length) return;
                String name = saves[idx].fullName.toString();
                if (selected.contains(name)) {
                    RECTANGLE body = node.body();
                    COLOR.YELLOW100.render(r, body.x1(), body.x2(), body.y1(), body.y2());
                }
            } catch (Throwable t) { /* swallow */ }
            return;
        }
        if (node instanceof GuiSection) {
            for (RENDEROBJ child : iterChildren((GuiSection) node)) {
                highlightSavebutts(r, child, widget, selected, depth + 1);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Iterable<RENDEROBJ> iterChildren(GuiSection section) {
        try {
            Object list = GS_renderables.get(section);
            // ArrayListResize<RENDEROBJ> implements LIST<RENDEROBJ> which extends Iterable.
            return (Iterable<RENDEROBJ>) list;
        } catch (Throwable t) {
            return java.util.Collections.emptyList();
        }
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException e) { /* keep walking */ }
        }
        return null;
    }

    /* ----------------------------------------------------------------- state */

    private static final class State {
        final Set<String> selectedNames = new LinkedHashSet<>();
        String[] prevNames = null;
        int lastSeen = -1;
    }
}
