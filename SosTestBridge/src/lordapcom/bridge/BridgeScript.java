package lordapcom.bridge;

import java.io.IOException;

import game.GAME;
import script.SCRIPT;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

public final class BridgeScript implements SCRIPT {

	@Override
	public CharSequence name() {
		return "SosTestBridge";
	}

	@Override
	public CharSequence desc() {
		return "Developer tool: file based command bridge for testing.";
	}

	@Override
	public boolean isSelectable() {
		return false;
	}

	@Override
	public boolean forceInit() {
		return true;
	}

	@Override
	public SCRIPT_INSTANCE createInstance() {
		return new SCRIPT_INSTANCE() {
			{
				// GameSaver/GAME.update loop: runs after every resource has updated, i.e. on a tick boundary.
				GAME.addAfterUpdate(Bridge::afterUpdate);
			}

			@Override
			public void update(double ds) {
				Bridge.tick(ds);
			}

			@Override
			public void save(FilePutter file) {
			}

			@Override
			public void load(FileGetter file) throws IOException {
			}
		};
	}
}
