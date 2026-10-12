package planahead;

import java.io.IOException;

import script.SCRIPT.SCRIPT_INSTANCE;
import settlement.room.main.construction.ConstructionAccess;
import snake2d.LOG;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import view.interrupter.ISidePanel;
import view.interrupter.ISidePanels;
import view.main.VIEW;

public class InstanceScript implements SCRIPT_INSTANCE {

    private final BuildMenuPatch menuPatch = new BuildMenuPatch();
    private final PlanModeWatch planWatch = new PlanModeWatch();

    @Override
    public void save(FilePutter file) {
        // No persisted state — patches and watcher state rebuild each session.
    }

    @Override
    public void load(FileGetter file) throws IOException {
        // Nothing to read.
    }

    @Override
    public void update(double ds) {
        try {
            updateBetterBlueprintsCompat();
        } catch (Throwable t) {
            LOG.ln("[PlanAhead] BB-compat error: " + t);
        }
        try {
            menuPatch.tick();
        } catch (Throwable t) {
            LOG.ln("[PlanAhead] menu patch error: " + t);
        }
        try {
            planWatch.tick();
        } catch (Throwable t) {
            LOG.ln("[PlanAhead] plan watcher error: " + t);
        }
        try {
            ConstructionAccess.enforceTechLock();
        } catch (Throwable t) {
            LOG.ln("[PlanAhead] tech-lock enforcer error: " + t);
        }
    }

    /**
     * Whenever the saved-prints side panel is open (vanilla or Better
     * Blueprints), strip the tech requirements from every room blueprint
     * so the picker shows tech-locked entries, the preview accepts them
     * and the actual placement stops silently skipping them.
     *
     * The global planMode flag is NOT touched here — researched rooms in
     * the same saved area should still come out active so workers start
     * building them. The {@link ConstructionAccess#enforceTechLock()}
     * watchdog will pick out just the tech-locked constructions placed
     * during this window and dormantise them. When the panel closes,
     * reqs are restored.
     */
    private void updateBetterBlueprintsCompat() {
        if (VIEW.s() == null) return;

        ISidePanel prints = VIEW.s().ui.prints;
        boolean panelOpen = false;
        if (prints != null) {
            ISidePanels parent = prints.last();
            panelOpen = parent != null && parent.added(prints);
        }

        if (panelOpen) {
            TechReqsHider.hide();
        } else if (TechReqsHider.isHidden()) {
            TechReqsHider.restore();
        }
    }
}
