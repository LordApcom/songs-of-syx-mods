package lordapcom.practice;

import java.io.IOException;

import game.time.TIME;
import script.SCRIPT;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

public final class MainScript implements SCRIPT {

	private static final int VERSION = 1;

	@Override
	public CharSequence name() {
		return "Practice Makes Perfect";
	}

	@Override
	public CharSequence desc() {
		return "Production gets better with experience instead of research, and fades when you stop.";
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
		Skills.clear();
		return new Instance();
	}

	private static final class Instance implements SCRIPT_INSTANCE {

		private int lastDay = -1;

		@Override
		public void update(double ds) {
			try {
				int d = TIME.days().bitsSinceStart();
				if (lastDay < 0)
					lastDay = d;
				for (; lastDay < d; lastDay++)
					Skills.day();
			} catch (Throwable t) {
				Skills.err("update", t);
			}
		}

		@Override
		public void save(FilePutter file) {
			file.i(VERSION);
			file.i(Skills.ALL.size());
			for (Skills.Skill k : Skills.ALL.values()) {
				file.chars(k.bo.key);
				file.d(k.m);
			}
		}

		// Saves without the mod have no data: everyone starts at zero experience.
		@Override
		public void load(FileGetter file) throws IOException {
			Skills.clear();
			lastDay = -1;
			if (file.i() != VERSION)
				return;
			int n = file.i();
			for (int i = 0; i < n; i++) {
				String key = file.chars();
				double m = file.d();
				Skills.Skill k = Skills.ALL.get(key);
				if (k != null)
					k.m = m;
			}
		}
	}
}
