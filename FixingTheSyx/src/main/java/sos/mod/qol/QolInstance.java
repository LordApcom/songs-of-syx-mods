package sos.mod.qol;

import java.io.IOException;

import script.SCRIPT.SCRIPT_INSTANCE;
import snake2d.LOG;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.Renderer;
import sos.mod.qol.decor.DecorationRepeater;
import sos.mod.qol.fish.FisheryAutoFill;
import sos.mod.qol.house.HouseMountainPatch;
import sos.mod.qol.road.RoadOnMountain;
import sos.mod.qol.saves.SaveMultiSelectGlue;
import sos.mod.qol.support.SupportPillarFiller;
import view.menu.QolSaveMultiSelect;

public class QolInstance implements SCRIPT_INSTANCE {

    private boolean patchedHouseMountain = false;

    private final DecorationRepeater decorRepeater = new DecorationRepeater();
    private final FisheryAutoFill fisheryAutoFill = new FisheryAutoFill();
    private final SaveMultiSelectGlue saveMultiSelect = new SaveMultiSelectGlue();
    private final RoadOnMountain roadOnMountain = new RoadOnMountain();
    private final SupportPillarFiller supportPillarFiller = new SupportPillarFiller();

    @Override
    public void save(FilePutter file) {
        // No persisted state.
    }

    @Override
    public void load(FileGetter file) throws IOException {
        // No persisted state.
    }

    @Override
    public void update(double ds) {
        if (!patchedHouseMountain) {
            try {
                HouseMountainPatch.install();
                patchedHouseMountain = true;
                LOG.ln("[QualityOfLife] house-on-mountain patch installed");
            } catch (Throwable t) {
                LOG.ln("[QualityOfLife] FAILED to install house-on-mountain patch: " + t);
                patchedHouseMountain = true; // don't retry
            }
        }
        try {
            decorRepeater.tick();
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] decor repeater error: " + t);
        }
        try {
            fisheryAutoFill.tick();
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] fishery auto-fill error: " + t);
        }
        try {
            saveMultiSelect.tick();
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] save multi-select error: " + t);
        }
        try {
            roadOnMountain.tick();
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] road-on-mountain error: " + t);
        }
        try {
            supportPillarFiller.tick();
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] support pillar tick error: " + t);
        }
    }

    @Override
    public void hover(snake2d.util.datatypes.COORDINATE mCoo, boolean mouseHasMoved) {
        // Runs in the SCRIPT hover phase, BEFORE the camera consumes the mouse wheel.
        // Lets the decoration tool claim Ctrl+Alt / Ctrl+Shift wheel scrolls (repeat count
        // and series spacing) so they act on the decoration instead of only zooming.
        try {
            view.tool.QolFixedHook hook = view.tool.QolFixedHook.INSTANCE;
            if (hook != null) hook.handlePreZoomWheel();
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] decor wheel handler error: " + t);
        }
    }

    @Override
    public void render(Renderer r, float ds) {
        try {
            QolSaveMultiSelect.renderOverlay(r);
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] save overlay render error: " + t);
        }
        try {
            roadOnMountain.render(r, ds);
        } catch (Throwable t) {
            LOG.ln("[QualityOfLife] road overlay render error: " + t);
        }
    }
}
