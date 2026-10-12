package lordapcom.cdream;

import java.io.IOException;

import script.SCRIPT;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

/** Entry point. Logic lives in the mixins and in {@link Island}; only the island flag is saved. */
public final class CDreamScript implements SCRIPT {

	private static final int SAVE_VERSION = 1;

	@Override
	public CharSequence name() {
		return "Cartographer's Dream";
	}

	@Override
	public CharSequence desc() {
		return "Dramatic settlement maps, a capital almost anywhere, and a playable sea-locked capital.";
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
		Island.reset();
		return new SCRIPT_INSTANCE() {
			@Override
			public void update(double ds) {
				try {
					Island.update(ds);
				} catch (Throwable t) {
					Log.once("Island.update", t);
				}
				try {
					Boats.update(ds);
				} catch (Throwable t) {
					Log.once("Boats.update", t);
				}
			}

			@Override
			public void save(FilePutter file) {
				file.i(SAVE_VERSION);
				file.bool(Island.island);
			}

			@Override
			public void load(FileGetter file) throws IOException {
				int version = file.i();
				if (version >= 1)
					Island.restore(file.bool());
			}
		};
	}
}
