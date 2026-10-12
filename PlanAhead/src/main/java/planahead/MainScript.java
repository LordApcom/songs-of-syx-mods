package planahead;

import script.SCRIPT;

public class MainScript implements SCRIPT {

    @Override
    public CharSequence name() {
        return "Plan Ahead";
    }

    @Override
    public CharSequence desc() {
        return "Lets you place tech-locked buildings as planning-mode constructions.";
    }

    @Override
    public SCRIPT_INSTANCE createInstance() {
        return new InstanceScript();
    }

    @Override
    public boolean forceInit() {
        return true;
    }
}
