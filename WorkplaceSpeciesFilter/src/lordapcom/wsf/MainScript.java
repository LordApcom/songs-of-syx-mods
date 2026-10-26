package lordapcom.wsf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import init.type.HGROUP;
import script.SCRIPT;
import settlement.main.SETT;
import settlement.room.main.Room;
import settlement.room.main.RoomInstance;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import view.ui.message.MessageText;

public final class MainScript implements SCRIPT {

	private static final int VERSION = 1;

	@Override
	public CharSequence name() {
		return "Workplace Species Filter";
	}

	@Override
	public CharSequence desc() {
		return "Per-building hard filters for who may work in a workplace and who may use a service.";
	}

	// Always on and not listed in the new-game menu. Saves made without the mod simply have no data (no filters).
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
		Filters.clear();
		return new Instance();
	}

	private static final class Pending {
		int idx, x, y;
		int[] w, u;
	}

	private static final class Instance implements SCRIPT_INSTANCE {

		private ArrayList<Pending> pending;
		private int tick;
		private boolean warned; // checked once per game started or loaded

		@Override
		public void update(double ds) {
			try {
				if (!warned) {
					warned = true;
					if (!polyphonActive())
						warnNoPolyphon();
				}
				if (pending != null) {
					resolve();
					pending = null;
				}
				if (++tick % 600 == 0)
					Filters.prune();
			} catch (Throwable t) {
				Filters.err("update", t);
			}
		}

		// Without Polyphon's agent no hook of this mod runs: no Tools menu entry, no filtering. Players reported "can't find
		// the filter" (Steam, 2026-10-21..26); this is the one case where the entry is missing, so say it in the game.
		private static boolean polyphonActive() {
			try {
				return vizardalpha.polyphon.api.Polyphon.isActive();
			} catch (Throwable t) {
				return false; // Polyphon not installed or not enabled
			}
		}

		private static void warnNoPolyphon() {
			snake2d.LOG.err("[WSF] Polyphon is not active: the mod's hooks did not load (no Tools menu entry, no filtering)");
			new MessageText("Workplace Species Filter is not running",
				"Polyphon is not active, so this mod can't add its Species filter tool. Make sure Polyphon is enabled in the launcher, then close and start the game again: the first time Polyphon runs it sets itself up and needs one restart.")
				.send();
		}

		// Rooms are rebuilt on load, so entries are re-attached by room index and position once the game runs.
		private void resolve() {
			for (Pending p : pending) {
				if (p.idx < 0 || p.idx >= SETT.ROOMS().map.max())
					continue;
				Room r = SETT.ROOMS().map.getByIndex(p.idx);
				if (r instanceof RoomInstance && ((RoomInstance) r).mX() == p.x && ((RoomInstance) r).mY() == p.y)
					Filters.put((RoomInstance) r, p.w, p.u);
			}
		}

		@Override
		public void save(FilePutter file) {
			ArrayList<Map.Entry<RoomInstance, Filters.Entry>> l = Filters.live();
			file.i(VERSION);
			file.i(HGROUP.all().size());
			file.i(l.size());
			for (Map.Entry<RoomInstance, Filters.Entry> e : l) {
				RoomInstance ins = e.getKey();
				file.i(ins.index());
				file.i(ins.mX());
				file.i(ins.mY());
				for (int[] b : new int[][] { e.getValue().w, e.getValue().u }) {
					file.bool(b != null);
					if (b != null)
						file.is(b);
				}
			}
		}

		@Override
		public void load(FileGetter file) throws IOException {
			Filters.clear();
			pending = null;
			int ver = file.i();
			if (ver != VERSION)
				return; // unknown layout: the game skips the rest by the saved size
			int groups = file.i();
			int n = file.i();
			int words = (groups + 31) >>> 5;
			boolean usable = groups == HGROUP.all().size(); // species list changed: read and drop
			ArrayList<Pending> l = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				Pending p = new Pending();
				p.idx = file.i();
				p.x = file.i();
				p.y = file.i();
				for (int k = 0; k < 2; k++) {
					int[] b = null;
					if (file.bool()) {
						b = new int[words];
						file.is(b);
					}
					if (k == 0)
						p.w = b;
					else
						p.u = b;
				}
				l.add(p);
			}
			if (usable)
				pending = l;
		}
	}
}
