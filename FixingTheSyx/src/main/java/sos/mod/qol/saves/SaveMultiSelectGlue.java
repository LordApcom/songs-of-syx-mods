package sos.mod.qol.saves;

import snake2d.LOG;
import view.menu.QolSaveMultiSelect;

/**
 * Glue between {@link sos.mod.qol.QolInstance#update(double)} and the same-package patcher
 * that lives in {@code view.menu}. Just calls into the patcher every tick.
 */
public final class SaveMultiSelectGlue {

    public void tick() {
        try {
            QolSaveMultiSelect.tick();
        } catch (Throwable t) {
            LOG.ln("[FixingTheSyx] save multi-select tick failed: " + t);
        }
    }
}
