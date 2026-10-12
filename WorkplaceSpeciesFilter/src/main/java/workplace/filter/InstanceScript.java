package workplace.filter;

import script.SCRIPT.SCRIPT_INSTANCE;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import workplace.filter.ui.ModuleRegistrar;
import workplace.filter.ui.WorkplaceAssignTool;

import java.io.IOException;

public class InstanceScript implements SCRIPT_INSTANCE {

    private final WorkplaceFilterManager manager;

    {
        manager = new WorkplaceFilterManager();
        WorkplaceFilterManager.INSTANCE = manager;
    }
    private final WorkplaceAssignTool tool = new WorkplaceAssignTool(manager);
    private final ModuleRegistrar registrar = new ModuleRegistrar(tool);

    @Override
    public void update(double ds) {
        registrar.tryRegister();
        manager.enforce(ds);
    }

    @Override
    public void save(FilePutter file) {
        manager.save(file);
    }

    @Override
    public void load(FileGetter file) throws IOException {
        manager.load(file);
    }
}
