package workplace.filter.ui;

import view.sett.ui.home.WorkplaceFilterInjector;

/**
 * Handles registration of the WorkplaceAssignTool button into the game UI.
 * Uses same-package placement (WorkplaceFilterInjector in view.sett.ui.home)
 * for reliable access to UIHomes' internal GuiSection.
 */
public class ModuleRegistrar {

    private boolean registered = false;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 600; // ~10 seconds at 60fps
    private final WorkplaceAssignTool tool;

    public ModuleRegistrar(WorkplaceAssignTool tool) {
        this.tool = tool;
    }

    /**
     * Attempts to inject the tool button into the Housing panel.
     * Called from update() each tick until success or max retries.
     */
    public void tryRegister() {
        if (registered) return;
        if (retryCount >= MAX_RETRIES) return;
        retryCount++;

        try {
            registered = WorkplaceFilterInjector.inject(tool);
            if (registered) {
                System.out.println("[WorkplaceSpeciesFilter] Tool button added to Housing panel.");
            }
        } catch (Exception e) {
            // Retry next tick
        }
    }
}
