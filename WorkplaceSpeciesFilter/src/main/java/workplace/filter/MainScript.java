package workplace.filter;

import script.SCRIPT;

public class MainScript implements SCRIPT {

    static {
        // Scan for class-replacement collisions with other active mods and log them
        // to the game log so users can include conflict info in bug reports.
        ConflictDetector.runOnce();
    }

    @Override
    public CharSequence name() {
        return "Workplace Species Filter";
    }

    @Override
    public CharSequence desc() {
        return "Adds per-building species filtering to all workplaces.";
    }

    @Override
    public SCRIPT_INSTANCE createInstance() {
        return new InstanceScript();
    }

    @Override
    public boolean forceInit() {
        return true; // load into existing saves
    }
}
