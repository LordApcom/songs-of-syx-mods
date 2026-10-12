package sos.mod.qol.decor;

import snake2d.LOG;
import view.tool.QolFixedHook;

/**
 * Glue between the QolInstance update loop and the {@link QolFixedHook} that lives in
 * {@code view.tool}. We retry the install on every tick until it succeeds because the
 * settlement view is constructed lazily after the script first loads.
 */
public final class DecorationRepeater {

    private boolean installed = false;
    private int retries = 0;

    public void tick() {
        if (installed) return;
        if (retries++ > 600) return; // ~10s worth of frames; stop spamming
        try {
            QolFixedHook.install();
            installed = true;
            LOG.ln("[QualityOfLife] decoration repeater installed");
        } catch (Throwable t) {
            // Settlement view may not exist yet (e.g. main menu). Retry next tick.
        }
    }
}
