package cartographers.dream;

import script.SCRIPT;

public class MainScript implements SCRIPT {

    @Override
    public CharSequence name() {
        return "Cartographer's Dream";
    }

    @Override
    public CharSequence desc() {
        return "Settlement-map generation overhaul plus relaxed capital placement rules.";
    }

    @Override
    public void initBeforeGameInited() {
        // Register the ferry HalfEntity factory into the freshly-built
        // HalfEnts BEFORE the save is deserialized. This hook runs during
        // game construction (GAME creates SETT — and thus HalfEnts — then
        // calls this), which is before SETT.load pours the save into its
        // resources. Registering here guarantees the ferry factory occupies
        // its index in the constructors list when HalfEnts.load looks up
        // persisted ferries by index, avoiding the load-time crash.
        cartographers.dream.ferry.FerryFactoryHolder.get();
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
