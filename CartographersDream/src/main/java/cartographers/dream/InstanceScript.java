package cartographers.dream;

import java.io.IOException;

import cartographers.dream.ferry.FerryPassengers;
import cartographers.dream.ferry.FerryScenario;
import script.SCRIPT.SCRIPT_INSTANCE;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

public class InstanceScript implements SCRIPT_INSTANCE {

    @Override
    public void update(double ds) {
        // Eagerly install the FerryFactory the first time we get a tick
        // with SETT.HALFENTS() ready. Cheap no-op on subsequent ticks
        // (early return when instance is already cached). We do this from
        // update — not from a SCRIPT.init hook — because SETT.HALFENTS()
        // is only created after a save is loaded / settlement is built,
        // not at game-start mod-load time.
        cartographers.dream.ferry.FerryFactoryHolder.get();
        FerryPassengers.tick(ds);
        world.map.pathing.CDPathConnector.run();
    }

    @Override
    public void save(FilePutter file) {
        FerryPassengers.save(file);
    }

    @Override
    public void load(FileGetter file) throws IOException {
        // Existing saves predating ferry feature have no FerryPassengers
        // block. Guard with try/catch so old saves don't crash on load.
        try {
            FerryPassengers.load(file);
        } catch (Throwable t) {
            FerryPassengers.clear();
        }
        // Detection caches are settlement-local; force recompute on load.
        FerryScenario.reset();
        // Re-attempt world-map graph connection on load. If we already
        // wrote edges before save, they persist in WDirMap and our check
        // hasAnyEdge will short-circuit. Cheap to re-run.
        world.map.pathing.CDPathConnector.resetDone();
    }
}
