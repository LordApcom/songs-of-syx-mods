package workplace.filter.ui;

import init.constant.C;
import init.race.RACES;
import init.race.Race;
import init.sprite.SPRITES;
import init.sprite.UI.UI;
import init.type.HCLASSES;
import init.type.HGROUP;
import init.type.HGROUP.HTypeBitsImp;
import settlement.main.ON_TOP_RENDERABLE;
import settlement.main.SETT;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.service.module.RoomService.ROOM_SERVICE_HASER;
import snake2d.Renderer;
import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.misc.CLAMP;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import snake2d.util.sprite.SPRITE;
import util.gui.misc.GButt;
import util.rendering.RenderData;
import util.rendering.RenderData.RenderIterator;
import util.rendering.ShadowBatch;
import view.tool.PLACER_TYPE;
import view.tool.PlacableMulti;
import workplace.filter.WorkplaceFilterManager;

/**
 * PlacableMulti tool for painting species filters onto workplaces.
 * Replicates the same UX as the housing UIHomeAssign tool,
 * including the on-map icon overlay showing filter state.
 */
public class WorkplaceAssignTool extends PlacableMulti {

    private static final String NAME = "Assign Species";
    private static final String DESC = "Lets you assign workplaces to specific species criteria.";
    private static final String ERR = "Must be placed on a workplace.";
    private static final String ERR_SERVICE = "Must be placed on a service building.";

    public enum Mode { WORKERS, USERS }

    private Mode currentMode = Mode.WORKERS;
    private final HTypeBitsImp workerData = new HTypeBitsImp(false);
    private final HTypeBitsImp userData = new HTypeBitsImp(false);

    private HTypeBitsImp currentData() {
        return currentMode == Mode.USERS ? userData : workerData;
    }

    private final LIST<CLICKABLE> butts;
    private final WorkplaceFilterManager manager;

    public WorkplaceAssignTool(WorkplaceFilterManager manager) {
        super(NAME, DESC, SPRITES.icons().m.citizen);
        this.manager = manager;

        workerData.setEveryone();
        userData.setEveryone();

        GuiSection sec = new GuiSection();

        // MOD: mode toggle (Workers/Users)
        GuiSection modeRow = new GuiSection();

        CLICKABLE workersBtn = new GButt.ButtPanel(SPRITES.icons().m.citizen) {
            @Override
            protected void clickA() {
                currentMode = Mode.WORKERS;
            }

            @Override
            protected void renAction() {
                selectedSet(currentMode == Mode.WORKERS);
            }
        }.hoverInfoSet("Filter who works at this building");

        CLICKABLE usersBtn = new GButt.ButtPanel(SPRITES.icons().m.noble) {
            @Override
            protected void clickA() {
                currentMode = Mode.USERS;
            }

            @Override
            protected void renAction() {
                selectedSet(currentMode == Mode.USERS);
            }
        }.hoverInfoSet("Filter who uses this building's service");

        modeRow.addRightC(0, workersBtn);
        modeRow.addRightC(4, usersBtn);
        sec.add(modeRow);

        // Quick buttons row (Everyone / All Citizens / All Slaves / None)
        GuiSection quickRow = new GuiSection();

        // "Everyone" button (placed in quickRow below the mode toggle)
        quickRow.addRightC(0, new GButt.ButtPanel(SPRITES.icons().m.questionmark) {
            @Override
            protected void clickA() {
                currentData().setEveryone();
            }
        }.hoverInfoSet("Set permission for everyone"));

        // "All Citizens" button
        quickRow.addRightC(0, new GButt.ButtPanel(HCLASSES.CITIZEN().icon()) {
            @Override
            protected void clickA() {
                currentData().clear();
                for (Race r : RACES.all())
                    currentData().set(HGROUP.get(HCLASSES.CITIZEN(), r));
            }
        }.hoverInfoSet("Set permission for All: " + HCLASSES.CITIZEN().names));

        // "All Slaves" button
        quickRow.addRightC(0, new GButt.ButtPanel(HCLASSES.SLAVE().icon()) {
            @Override
            protected void clickA() {
                currentData().clear();
                for (Race r : RACES.all())
                    currentData().set(HGROUP.get(HCLASSES.SLAVE(), r));
            }
        }.hoverInfoSet("Set permission for All: " + HCLASSES.SLAVE().names));

        // "None" button
        quickRow.addRightC(0, new GButt.ButtPanel(SPRITES.icons().m.cancel) {
            @Override
            protected void clickA() {
                currentData().clear();
            }
        }.hoverInfoSet("Set permission for none"));

        sec.addRelBody(4, DIR.S, quickRow);

        // Species toggle grid
        GuiSection grid = new GuiSection();
        for (HGROUP t : HGROUP.all()) {
            CLICKABLE btn = new GButt.ButtPanel(t.icon) {
                @Override
                protected void clickA() {
                    if (selectedIs())
                        currentData().clear(t);
                    else
                        currentData().set(t);
                }

                @Override
                protected void renAction() {
                    selectedSet(currentData().is(t));
                }
            }.hoverInfoSet("Set permission for: " + t.name);

            if (grid.getLastX2() > 500) {
                btn.body().moveX1(0).moveY1(grid.body().y2());
                grid.add(btn);
            } else {
                grid.addRightC(0, btn);
            }
        }

        sec.addRelBody(8, DIR.S, grid);
        butts = new ArrayList<CLICKABLE>(sec);
    }

    @Override
    public CharSequence isPlacable(int tx, int ty, AREA area, PLACER_TYPE type) {
        if (currentMode == Mode.USERS) {
            if (isServiceBuilding(tx, ty)) return null;
            return ERR_SERVICE;
        }
        // WORKERS mode
        if (isWorkplace(tx, ty)) return null;
        return ERR;
    }

    @Override
    public void place(int tx, int ty, AREA area, PLACER_TYPE type) {
        Room room = SETT.ROOMS().map.get(tx, ty);
        if (!(room instanceof RoomInstance)) return;
        RoomInstance ins = (RoomInstance) room;

        HTypeBitsImp copy = new HTypeBitsImp(false);
        copy.copy(currentData());

        if (currentMode == Mode.USERS) {
            manager.setUserFilter(ins, copy);
        } else {
            manager.setWorkerFilter(ins, copy);
        }
    }

    private boolean isServiceBuilding(int tx, int ty) {
        Room room = SETT.ROOMS().map.get(tx, ty);
        if (room instanceof RoomInstance) {
            RoomInstance ins = (RoomInstance) room;
            // Broad check: any building with a RoomService (includes shrines/temples,
            // which use plain ROOM_SERVICE_HASER rather than ROOM_SERVICE_ACCESS_HASER)
            return ins.blueprint() instanceof ROOM_SERVICE_HASER;
        }
        return false;
    }

    @Override
    public boolean expandsTo(int fromX, int fromY, int toX, int toY) {
        Room fromRoom = SETT.ROOMS().map.get(fromX, fromY);
        if (fromRoom == null) return false;
        return fromRoom.isSame(fromX, fromY, toX, toY);
    }

    @Override
    public LIST<CLICKABLE> getAdditionalButt() {
        ren.add(); // register the on-map overlay
        return butts;
    }

    private boolean isWorkplace(int tx, int ty) {
        Room room = SETT.ROOMS().map.get(tx, ty);
        if (room instanceof RoomInstance) {
            RoomInstance ins = (RoomInstance) room;
            if (ins.blueprint() instanceof RoomBlueprintIns) {
                RoomBlueprintIns<?> bp = (RoomBlueprintIns<?>) ins.blueprint();
                return bp.employment() != null && ins.blueprint() != SETT.ROOMS().HOME;
            }
        }
        return false;
    }

    // --- On-map overlay: shows filter icons on workplaces ---

    private final ON_TOP_RENDERABLE ren = new ON_TOP_RENDERABLE() {

        @Override
        public void render(Renderer r, ShadowBatch shadowBatch, RenderData renderData, double ds) {
            RenderIterator it = renderData.onScreenTiles();
            while (it.has()) {
                int tx = it.tx();
                int ty = it.ty();
                boolean valid = (currentMode == Mode.USERS)
                        ? isServiceBuilding(tx, ty)
                        : isWorkplace(tx, ty);
                if (valid) {
                    Room room = SETT.ROOMS().map.get(tx, ty);
                    if (room instanceof RoomInstance) {
                        renderRoom(r, (RoomInstance) room, it);
                    }
                }
                it.next();
            }
            remove();
        }

        private final ArrayList<HGROUP> rens = new ArrayList<>(HGROUP.all().size());

        private void renderRoom(Renderer r, RoomInstance ins, RenderIterator it) {
            // Calculate center of room in screen coordinates
            double dx = ins.body().x1() + ins.body().width() * 0.5;
            dx = dx - it.tx();
            int cx = (int) (it.x() + dx * C.TILE_SIZE);

            double dy = ins.body().y1() + ins.body().height() * 0.5;
            dy = dy - it.ty();
            int cy = (int) (it.y() + dy * C.TILE_SIZE);

            // MOD: Get filter state based on current mode
            HTypeBitsImp bits;
            if (currentMode == Mode.USERS) {
                bits = manager.getUserFilter(ins);
            } else {
                bits = manager.getWorkerFilter(ins);
            }

            // Count allowed groups
            int am = 0;
            HGROUP single = null;
            if (bits == null) {
                // No filter = everyone
                am = HGROUP.all().size();
            } else {
                for (HGROUP hh : HGROUP.all()) {
                    if (bits.is(hh)) {
                        single = hh;
                        am++;
                    }
                }
            }

            if (am == HGROUP.all().size()) {
                // Everyone allowed - show ?
                renderSingle(r, cx, cy, UI.icons().m.questionmark);
            } else if (am == 0) {
                // Nobody allowed - show cancel
                renderSingle(r, cx, cy, UI.icons().m.cancel);
            } else if (am == 1) {
                // Single species - show that species icon
                renderSingle(r, cx, cy, single.icon);
            } else if (am < HGROUP.all().size() / 2) {
                // Few allowed - show allowed icons
                rens.clearSloppy();
                for (HGROUP hh : HGROUP.all()) {
                    if (bits.is(hh)) rens.add(hh);
                }
                renderMany(r, cx, cy, ins, false);
            } else {
                // Many allowed - show NOT-allowed icons with anti overlay
                rens.clearSloppy();
                for (HGROUP hh : HGROUP.all()) {
                    if (!bits.is(hh)) rens.add(hh);
                }
                if (rens.size() > 0)
                    renderMany(r, cx, cy, ins, true);
            }
        }

        private void renderSingle(Renderer r, int cx, int cy, SPRITE icon) {
            int w = icon.width() * C.SCALE;
            int h = icon.height() * C.SCALE;
            int x1 = cx - w / 2;
            int y1 = cy - h / 2;
            icon.render(r, x1, x1 + w, y1, y1 + h);
        }

        private void renderMany(Renderer r, int cx, int cy, RoomInstance room, boolean anti) {
            int width = room.body().width() * C.TILE_SIZE;
            int w = 24 * C.SCALE / 2;
            int h = rens.get(0).icon.height() * C.SCALE / 2;

            int dx = (width / rens.size());
            dx = CLAMP.i(dx, 1, w);

            int x1 = cx - dx * rens.size() / 2;
            int y1 = cy - h;

            for (HGROUP t : rens) {
                t.icon.render(r, x1, x1 + w, y1, y1 + h);
                if (anti)
                    UI.icons().m.anti.render(r, x1, x1 + w, y1, y1 + h);
                x1 += dx;
            }
        }
    };
}
