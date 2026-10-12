package lordapcom.planahead;

import java.io.IOException;

import script.SCRIPT;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

/** Entry point only. The work is done by the Polyphon mixins; nothing essential is saved. */
public final class PlanAheadScript implements SCRIPT {

	@Override
	public CharSequence name() {
		return "Plan Ahead";
	}

	@Override
	public CharSequence desc() {
		return "Plan any building before its technology is researched.";
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
		PlanAhead.reset();
		return new SCRIPT_INSTANCE() {
			@Override
			public void update(double ds) {
				// safety net: never leave Planning Mode forced if a creation aborted midway
				PlanAhead.endCreate();
			}

			@Override
			public void save(FilePutter file) {
				file.i(1);
			}

			@Override
			public void load(FileGetter file) throws IOException {
				file.i();
			}
		};
	}
}
