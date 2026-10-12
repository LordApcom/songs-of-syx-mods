package sos.mod.qol;

import script.SCRIPT;

public class QolScript implements SCRIPT {

    @Override
    public CharSequence name() {
        return "Fixing the Syx";
    }

    @Override
    public CharSequence desc() {
        return "Houses on mountains, decoration series placer, and river fishery auto-fill.";
    }

    @Override
    public SCRIPT_INSTANCE createInstance() {
        return new QolInstance();
    }

    @Override
    public boolean forceInit() {
        return true;
    }
}
