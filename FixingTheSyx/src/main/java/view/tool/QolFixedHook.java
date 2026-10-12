package view.tool;

import java.lang.reflect.Field;

import init.constant.C;
import init.sprite.SPRITES;
import settlement.environment.SettEnvMap;
import settlement.environment.SettEnvMap.SettEnv;
import settlement.main.SETT;
import settlement.room.infra.monument.ROOM_MONUMENT;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.category.RoomCategorySub;
import settlement.room.main.furnisher.Furnisher;
import settlement.room.main.furnisher.FurnisherItem;
import snake2d.CORE;
import snake2d.KEYCODES;
import snake2d.MButt;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.text.Dic;
import view.keyboard.KEYS;
import view.keyboard.KeyButt;
import view.main.VIEW;
import view.subview.GameWindow;

/**
 * Drop-in replacement for {@link PlacableFixedTool}. Re-implements the vanilla single-item
 * placement loop and adds an extra mode: while a decoration is active, holding Ctrl+Alt and
 * scrolling sets a <em>repeat count</em>; the next click then places that many copies in a
 * straight line at sensible spacing (twice the item's effect radius for monuments, item
 * width otherwise). Pressing R rotates the series direction (E → S → W → N) in addition to
 * any vanilla item-rotation behavior the placable already supports.
 */
public final class QolFixedHook extends ToolPlacer.placeFunc {

    private PlacableFixed placable;
    private int repeatCount = 1;
    private boolean repeatEligible = false;
    /** Series direction index — 0=east, 1=south, 2=west, 3=north. */
    private int seriesDir = 0;
    /**
     * Manual spacing override; -1 = auto (radius-based or width). >=1 = N tiles between centers.
     * Static so an external handler (running before the tool consumes the wheel) can mutate it.
     */
    public static int spacingOverride = -1;

    private final ArrayList<CLICKABLE> butts = new ArrayList<>(3);

    private final CLICKABLE bIncrease = KeyButt.wrap(new GButt.Panel(SPRITES.icons().m.plus) {
        { hoverInfoSet("" + KEYS.MAIN().MOD.repr() + Dic.¤¤MouseWheelAdd); }
        @Override protected void renAction() {
            activeSet(placable != null && placable.size() < placable.sizes() - 1);
        }
        @Override protected void clickA() {
            if (placable != null && placable.size() < placable.sizes() - 1)
                placable.sizeSet(placable.size() + 1);
        }
    }, KEYS.MAIN().GROW);

    private final CLICKABLE bDecrease = KeyButt.wrap(new GButt.Panel(SPRITES.icons().m.minus) {
        { hoverInfoSet("" + KEYS.MAIN().MOD.repr() + Dic.¤¤MouseWheelAdd); }
        @Override protected void renAction() {
            activeSet(placable != null && placable.size() > 0);
        }
        @Override protected void clickA() {
            if (placable != null && placable.size() > 0)
                placable.sizeSet(placable.size() - 1);
        }
    }, KEYS.MAIN().SHRINK);

    private final CLICKABLE bRotate = KeyButt.wrap(new GButt.Panel(SPRITES.icons().m.rotate) {
        @Override protected void clickA() {
            if (placable == null) return;
            cycleRotation();
        }
    }, KEYS.MAIN().ROTATE);

    /**
     * The installed hook, so the SCRIPT-level hover() phase (which runs before the
     * camera-zoom consumes the mouse wheel) can drive the decoration repeat/spacing
     * wheel handling on the live tool. See {@link #handlePreZoomWheel()}.
     */
    public static QolFixedHook INSTANCE;

    public static void install() throws ReflectiveOperationException {
        ToolPlacer placer = VIEW.s().tools.placer;
        Field f = ToolPlacer.class.getDeclaredField("fixed");
        f.setAccessible(true);
        Object current = f.get(placer);
        if (current instanceof QolFixedHook) { INSTANCE = (QolFixedHook) current; return; }
        QolFixedHook hook = new QolFixedHook();
        f.set(placer, hook);
        INSTANCE = hook;
    }

    @Override
    void updateHovered(float ds, GameWindow window, boolean pressed) {
        if (placable == null) return;

        // Ctrl+Alt+Wheel (repeat count) and Ctrl+Shift+Wheel (series spacing) used to live
        // here, but updateHovered runs LATE in the frame — after the camera-zoom check has
        // already consumed the wheel whenever Ctrl+Alt/Ctrl+Shift make the game's collapsed
        // KEYS modifier resolve to something other than lone-Ctrl (so KEYS.MAIN().MOD was
        // false, the zoom fired, and the decoration command was lost — the "Ctrl+Alt /
        // Ctrl+Shift only zoom" report). They now run in handlePreZoomWheel(), driven from
        // QolInstance.hover() which fires BEFORE the zoom, gated on raw keyboard state.

        // Vanilla Ctrl+Wheel: changes item size.
        double s = MButt.peekWheel();
        if (KEYS.MAIN().MOD.isPressed() && s != 0) {
            if (s > 0 && placable.size() < placable.sizes() - 1) {
                placable.sizeSet(placable.size() + 1);
            } else if (s < 0 && placable.size() > 0) {
                placable.sizeSet(placable.size() - 1);
            }
            MButt.clearWheelSpin();
        }

        if (KEYS.MAIN().GROW.consumeClick() && placable.size() < placable.sizes() - 1) {
            placable.sizeSet(placable.size() + 1);
        } else if (KEYS.MAIN().SHRINK.consumeClick() && placable.size() > 0) {
            placable.sizeSet(placable.size() - 1);
        }

        if (KEYS.MAIN().ROTATE.consumeClick()) {
            cycleRotation();
        }

        if (pressed) {
            click(window);
        }
    }

    /**
     * Ctrl+Alt+Wheel (repeat count) and Ctrl+Shift+Wheel (series spacing) for decorations.
     * Invoked from {@link sos.mod.qol.QolInstance#hover} which runs in the SCRIPT hover
     * phase — BEFORE the camera-zoom check gets a chance to consume the wheel. Gated on RAW
     * keyboard state (not KEYS.MAIN().MOD) because the game collapses all held modifiers to
     * a single slot: when Ctrl is combined with Alt or Shift that slot may not resolve to
     * lone-Ctrl, so MOD.isPressed() reads false and the old late handler never saw the
     * scroll. Consuming the wheel here (clearWheelSpin) stops the camera zooming.
     */
    public void handlePreZoomWheel() {
        if (placable == null || !repeatEligible) return;
        if (!ctrlDownRaw()) return;

        if (altDown()) {
            float rw = MButt.peekWheel();
            if (rw != 0) {
                if (rw > 0 && repeatCount < 64) repeatCount++;
                else if (rw < 0 && repeatCount > 1) repeatCount--;
                MButt.clearWheelSpin();
            }
            return;
        }
        if (shiftDownRaw()) {
            float sw = MButt.peekWheel();
            if (sw != 0) {
                if (spacingOverride < 0) spacingOverride = autoSpacing();
                if (sw > 0 && spacingOverride < 64) spacingOverride++;
                else if (sw < 0 && spacingOverride > 1) spacingOverride--;
                MButt.clearWheelSpin();
            }
        }
    }

    /**
     * Single source of truth for "R was pressed": rotate the series direction (always),
     * and rotate the item itself if the placable supports more than one orientation.
     */
    private void cycleRotation() {
        seriesDir = (seriesDir + 1) & 3;
        if (placable.rotations() > 1) {
            int r = (placable.rot() + 1) % placable.rotations();
            placable.rotSet(r);
        }
    }

    @Override
    void update(float ds, GameWindow window, boolean pressed) {
        placable.updateRegardless(window);
        super.update(ds, window, pressed);
    }

    @Override
    void render(SPRITE_RENDERER r, float ds, GameWindow window) {
        placable.init(window.tile().x(), window.tile().y());

        int w = placable.width();
        int h = placable.height();
        int x1 = window.tile().x() - w / 2;
        int y1 = window.tile().y() - h / 2;

        CharSequence pError = placable.placableWhole(x1, y1);
        CharSequence e = null;

        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                CharSequence e2 = placable.placable(x1 + dx, y1 + dy, dx, dy);
                if (e2 != null) e = e2;
            }
        }

        COLOR normal = (pError == null && e == null) ? GCOLOR.MAP().OK : GCOLOR.MAP().SOSO;

        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                CharSequence e2 = placable.placable(x1 + dx, y1 + dy, dx, dy);
                if (e2 != null) GCOLOR.MAP().BAD.bind();
                else normal.bind();

                int x = window.tile().rel().x() + (-w / 2 + dx) * C.TILE_SIZE;
                int y = window.tile().rel().y() + (-h / 2 + dy) * C.TILE_SIZE;

                int m = 0;
                if (dx == 0) m |= DIR.W.mask();
                if (dx == w - 1) m |= DIR.E.mask();
                if (dy == 0) m |= DIR.N.mask();
                if (dy == h - 1) m |= DIR.S.mask();
                m = ~m;
                m &= 0x0F;

                placable.renderPlaceHolder(r, m, x, y, x1 + dx, y1 + dy, dx, dy, e2 == null, pError == null);
            }
        }
        COLOR.unbind();

        int dist = (int) Math.ceil(h / 2.0 + 1);
        dist *= C.TILE_SIZE;
        dist = dist >> CORE.renderer().getZoomout();
        VIEW.hoverBoxDistance(dist);
        if (pError != null && pError.length() > 0) VIEW.hoverBox().error(pError);
        else if (e != null && e.length() > 0) VIEW.hoverBox().error(e);
        else placable.placeInfo(VIEW.hoverBox(), x1, y1);

        if (repeatEligible && repeatCount > 1) {
            renderRepeatPreview(r, window);
        }
    }

    @Override
    void click(GameWindow window) {
        placable.init(window.tile().x(), window.tile().y());
        int w = placable.width();
        int h = placable.height();
        int cx = window.tile().x();
        int cy = window.tile().y();
        int x1 = cx - w / 2;
        int y1 = cy - h / 2;

        if (placable.placableWhole(x1, y1) != null) return;
        for (int dy = 0; dy < h; dy++)
            for (int dx = 0; dx < w; dx++)
                if (placable.placable(x1 + dx, y1 + dy, dx, dy) != null) return;

        for (int dy = 0; dy < h; dy++)
            for (int dx = 0; dx < w; dx++)
                placable.place(x1 + dx, y1 + dy, dx, dy);
        placable.afterPlaced(x1, y1);

        if (repeatEligible && repeatCount > 1) {
            int spacing = spacingFor();
            int dx = dirX(seriesDir) * spacing;
            int dy = dirY(seriesDir) * spacing;
            for (int i = 1; i < repeatCount; i++) {
                placeAt(cx + dx * i, cy + dy * i);
            }
        }
    }

    private void placeAt(int cx, int cy) {
        placable.init(cx, cy);
        int w = placable.width();
        int h = placable.height();
        int x1 = cx - w / 2;
        int y1 = cy - h / 2;
        if (placable.placableWhole(x1, y1) != null) return;
        for (int dy = 0; dy < h; dy++)
            for (int dx = 0; dx < w; dx++)
                if (placable.placable(x1 + dx, y1 + dy, dx, dy) != null) return;
        for (int dy = 0; dy < h; dy++)
            for (int dx = 0; dx < w; dx++)
                placable.place(x1 + dx, y1 + dy, dx, dy);
        placable.afterPlaced(x1, y1);
    }

    private void renderRepeatPreview(SPRITE_RENDERER r, GameWindow window) {
        int spacing = spacingFor();
        int w = placable.width();
        int h = placable.height();
        int relX = window.tile().rel().x();
        int relY = window.tile().rel().y();
        int dirX = dirX(seriesDir);
        int dirY = dirY(seriesDir);
        for (int i = 1; i < repeatCount; i++) {
            int dxTiles = dirX * spacing * i;
            int dyTiles = dirY * spacing * i;
            int x1Rel = relX + (dxTiles - w / 2) * C.TILE_SIZE;
            int y1Rel = relY + (dyTiles - h / 2) * C.TILE_SIZE;
            int tx1 = window.tile().x() + dxTiles - w / 2;
            int ty1 = window.tile().y() + dyTiles - h / 2;

            CharSequence pError = placable.placableWhole(tx1, ty1);
            for (int dy = 0; dy < h; dy++) {
                for (int dx = 0; dx < w; dx++) {
                    CharSequence e2 = placable.placable(tx1 + dx, ty1 + dy, dx, dy);
                    if (e2 != null || pError != null) GCOLOR.MAP().BAD.bind();
                    else GCOLOR.MAP().SOSO.bind();
                    SPRITES.cons().BIG.dashed.render(r, 0,
                            x1Rel + dx * C.TILE_SIZE,
                            y1Rel + dy * C.TILE_SIZE);
                }
            }
        }
        COLOR.unbind();
    }

    @Override
    void activate(PLACABLE placer, GameWindow window) {
        this.placable = (PlacableFixed) placer;
        this.repeatEligible = isDecoration(placer);
        this.repeatCount = 1;
        this.seriesDir = 0;
        spacingOverride = -1;
    }

    /** Used by the SCRIPT-level spacing handler so it can clamp / initialize correctly. */
    public static int currentAutoSpacingOf(QolFixedHook hook) {
        if (hook == null) return 1;
        return hook.autoSpacing();
    }

    /** External access for the SCRIPT-level spacing handler. */
    public boolean isRepeatEligible() { return repeatEligible; }
    public int autoSpacingPublic() { return autoSpacing(); }
    public PlacableFixed currentPlacable() { return placable; }

    @Override
    void clickRelease(GameWindow window) { }

    @Override
    LIST<CLICKABLE> gui() {
        butts.clear();
        if (placable != null && placable.sizes() > 1) {
            butts.add(bDecrease);
            butts.add(bIncrease);
        }
        // Always show rotate button when in repeat mode, even if the placable itself has only
        // one rotation — the button now also rotates the series direction.
        if (placable != null && (placable.rotations() > 1 || repeatEligible)) {
            butts.add(bRotate);
        }
        return butts;
    }

    /**
     * Tile-spacing between successive copies in a series. Logic, in order of preference:
     *   1) ROOM_MONUMENT-derived rooms expose {@code radius(item)} for the current item, which
     *      already accounts for upgrade/size — use 2×radius so areas of effect just touch.
     *   2) Other decorations: pick the largest declared envRadius, denormalize against
     *      {@link SettEnvMap#RADIUS} (=16 tiles), spacing = 2×that.
     *   3) Fallback: place adjacent (item width).
     */
    private int spacingFor() {
        if (spacingOverride >= 1) return spacingOverride;
        return autoSpacing();
    }

    private int autoSpacing() {
        if (placable == null) return 1;
        int w = placable.width();

        Furnisher fur = furnisherOf(placable);
        if (fur != null) {
            RoomBlueprintImp blue = fur.blue();
            FurnisherItem item = currentItemOf(placable);
            if (blue instanceof ROOM_MONUMENT && item != null) {
                int r = (int) Math.round(((ROOM_MONUMENT) blue).radius(item));
                // 2r leaves the effect zones tangent (and visually a 1-tile gap appears
                // because radius is the half-extent excluding the source tile itself).
                // 2r - 1 forces a single-tile overlap, eliminating the gap.
                if (r > 0) return Math.max(w, 2 * r - 1);
            }
            int rTiles = maxEnvRadiusTiles(fur);
            if (rTiles > 0) return Math.max(w, 2 * rTiles - 1);
        }
        return Math.max(w, 1);
    }

    /** Reads the largest envRadius declared by {@code fur} and converts it to tile units. */
    private int maxEnvRadiusTiles(Furnisher fur) {
        try {
            Field rf = Furnisher.class.getDeclaredField("envRadius");
            rf.setAccessible(true);
            double[] envRadius = (double[]) rf.get(fur);
            double max = 0;
            for (double v : envRadius) if (v > max) max = v;
            if (max <= 0) return 0;
            return (int) Math.round(max * SettEnvMap.RADIUS);
        } catch (Throwable t) {
            return 0;
        }
    }

    private boolean isDecoration(PLACABLE p) {
        if (!(p instanceof PlacableFixed)) return false;
        Furnisher fur = furnisherOf((PlacableFixed) p);
        if (fur == null) return false;
        try {
            RoomBlueprintImp blue = fur.blue();
            RoomCategorySub decor = SETT.ROOMS().CATS.DECOR;
            return blue != null && blue.cat == decor;
        } catch (Throwable t) {
            return false;
        }
    }

    private Furnisher furnisherOf(PlacableFixed p) {
        try {
            Field gf = findField(p.getClass(), "group");
            if (gf == null) return null;
            gf.setAccessible(true);
            Object group = gf.get(p);
            if (group == null) return null;
            Field bf = group.getClass().getDeclaredField("blueprint");
            bf.setAccessible(true);
            Object blueprint = bf.get(group);
            return blueprint instanceof Furnisher ? (Furnisher) blueprint : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private FurnisherItem currentItemOf(PlacableFixed p) {
        try {
            Field gf = findField(p.getClass(), "group");
            if (gf == null) return null;
            gf.setAccessible(true);
            Object group = gf.get(p);
            if (group == null) return null;
            return (FurnisherItem) group.getClass()
                    .getMethod("item", int.class, int.class)
                    .invoke(group, p.size(), p.rot());
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException e) { /* keep walking */ }
        }
        return null;
    }

    private static int dirX(int dir) {
        switch (dir & 3) {
            case 0: return 1;   // east
            case 2: return -1;  // west
            default: return 0;
        }
    }

    private static int dirY(int dir) {
        switch (dir & 3) {
            case 1: return 1;   // south
            case 3: return -1;  // north
            default: return 0;
        }
    }

    private static boolean ctrlDownRaw() {
        return CORE.getInput().getKeyboard().isPressed(KEYCODES.KEY_LEFT_CONTROL)
            || CORE.getInput().getKeyboard().isPressed(KEYCODES.KEY_RIGHT_CONTROL);
    }

    private static boolean altDown() {
        return CORE.getInput().getKeyboard().isPressed(KEYCODES.KEY_LEFT_ALT)
            || CORE.getInput().getKeyboard().isPressed(KEYCODES.KEY_RIGHT_ALT);
    }

    private static boolean shiftDownRaw() {
        return CORE.getInput().getKeyboard().isPressed(KEYCODES.KEY_LEFT_SHIFT)
            || CORE.getInput().getKeyboard().isPressed(KEYCODES.KEY_RIGHT_SHIFT);
    }
}
