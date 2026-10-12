package lordapcom.fts;

import java.util.HashSet;

import script.SCRIPT;
import snake2d.LOG;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

/** Entry point. Holds no per-save data; the real work is done by the Polyphon mixins. */
public final class Fts implements SCRIPT {

	private static final HashSet<String> logged = new HashSet<>();

	/** Logs a failure once per place, so a broken hook never spams or crashes the game. */
	static void err(String where, Throwable t) {
		if (logged.add(where))
			LOG.ln("[FixingTheSyx] " + where + " failed, vanilla behaviour used: " + t);
	}

	@Override
	public CharSequence name() {
		return "Fixing the Syx";
	}

	@Override
	public CharSequence desc() {
		return "Quality-of-life pack for building.";
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
			@Override
			public void update(double ds) {
				try {
					RoadMountain.tick();
				} catch (Throwable t) {
					err("road tick", t);
				}
			}

			@Override
			public void save(FilePutter file) {
			}

			@Override
			public void load(FileGetter file) {
			}
		};
	}
}
