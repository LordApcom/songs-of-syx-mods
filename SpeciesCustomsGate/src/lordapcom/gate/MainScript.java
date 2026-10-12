package lordapcom.gate;

import java.io.IOException;
import java.util.Map;

import init.constant.C;
import init.type.HGROUP;
import script.SCRIPT;
import settlement.main.ON_TOP_RENDERABLE;
import settlement.main.SETT;
import settlement.path.AVAILABILITY;
import settlement.path.AvailabilityListener;
import snake2d.CORE;
import snake2d.KEYCODES;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.sprite.SPRITE;
import util.gui.misc.GBox;
import util.rendering.RenderData;
import util.rendering.ShadowBatch;
import view.main.VIEW;
import view.tool.Tool;

public final class MainScript implements SCRIPT {

	private static final int VERSION = 1;

	@Override
	public CharSequence name() {
		return "Species Customs Gate";
	}

	@Override
	public CharSequence desc() {
		return "Per-gate species and class filter with a one-way arrow. Hostiles and tourists always pass.";
	}

	// Always on and not listed in the new-game menu. A save made without the mod has no data (all gates open);
	// a save made with it loads fine without it (the game skips unknown script blocks by size).
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
		return new Instance();
	}

	private static final class Instance implements SCRIPT_INSTANCE {

		private boolean fWas, ready;

		// Needs the settlement, so it runs on the first tick rather than in the constructor.
		private void init(Session s) {
			ready = true;
			// Gate building, locking and room changes re-scan the gate segments (also rescanned every few seconds).
			new AvailabilityListener() {
				@Override
				protected void changed(int tx, int ty, AVAILABILITY a, AVAILABILITY old, boolean playerChange) {
					s.dirty = true;
				}
			};
			new ON_TOP_RENDERABLE() {
				@Override
				protected void render(Renderer r, ShadowBatch shadowBatch, RenderData d, double ds) {
					arrows(s, r, d);
				}
			}.add();
		}

		@Override
		public void update(double ds) {
			try {
				Session.CUR.remove();
				Session s = Session.get();
				if (s == null)
					return;
				if (!ready)
					init(s);
				s.tick();
				pollKey(s);
			} catch (Throwable t) {
				Session.err("update", t);
			}
		}

		// F opens the panel for the gate under the cursor (ScriptEngine does not forward key events, so poll).
		private void pollKey(Session s) {
			boolean down = CORE.getInput().getKeyboard().isPressed(KEYCODES.KEY_F)
					&& !CORE.getInput().getKeyboard().isPressed(KEYCODES.KEY_LEFT_CONTROL)
					&& !CORE.getInput().getKeyboard().isPressed(KEYCODES.KEY_RIGHT_CONTROL);
			boolean edge = down && !fWas;
			fWas = down;
			if (edge && !VIEW.inters().input.isActivated())
				openUnderMouse(s);
		}

		@Override
		public void mouseClick(MButt button) {
			if (button != MButt.RIGHT)
				return;
			try {
				Session s = Session.get();
				if (s != null)
					openUnderMouse(s);
			} catch (Throwable t) {
				Session.err("click", t);
			}
		}

		@Override
		public void hoverTimer(double mouseTimer, GBox text) {
			if (mouseTimer < 0.4 || !text.emptyIs())
				return;
			try {
				Session s = Session.get();
				Session.Gate g = s == null ? null : underMouse(s);
				if (g == null)
					return;
				text.title("Customs gate");
				text.add(text.text().normalify2().add(s.summary(g)));
				text.NL();
				text.add(text.text().normalify2().add("Arrow: " + Session.arrowName(g)));
				text.NL();
				text.add(text.text().normalify2().add("Right-click or F: edit filter"));
			} catch (Throwable t) {
				Session.err("hover", t);
			}
		}

		private static Session.Gate underMouse(Session s) {
			if (!VIEW.s().isActive() || !VIEW.s().tools.isHovered())
				return null;
			Tool t = VIEW.s().tools.current();
			if (t == null || !t.getClass().getSimpleName().equals("ToolDefault"))
				return null;
			COORDINATE c = VIEW.s().getWindow().tile();
			return s.gateAt(c.x(), c.y());
		}

		private static void openUnderMouse(Session s) {
			Session.Gate g = underMouse(s);
			if (g != null)
				GatePanel.open(s, g);
		}

		private static void arrows(Session s, Renderer r, RenderData d) {
			Session.Gate[] gs = s.gates;
			if (gs.length == 0)
				return;
			int w = SETT.TWIDTH;
			COLOR.YELLOW100.bind();
			for (Session.Gate g : gs) {
				if (g.x2 < d.tx1() || g.x1 > d.tx2() || g.y2 < d.ty1() || g.y1 > d.ty2())
					continue;
				SPRITE icon = GatePanel.arrowIcon(g.axisY, g.dir());
				for (int t : g.tiles) {
					int tx = t % w, ty = t / w;
					if (tx < d.tx1() || tx > d.tx2() || ty < d.ty1() || ty > d.ty2())
						continue;
					int x = d.x1() + (tx - d.tx1()) * C.TILE_SIZE;
					int y = d.y1() + (ty - d.ty1()) * C.TILE_SIZE;
					icon.render(r, x + C.TILE_SIZEH - 20, x + C.TILE_SIZEH + 20, y + C.TILE_SIZEH - 20,
							y + C.TILE_SIZEH + 20);
				}
			}
			COLOR.unbind();
		}

		@Override
		public void save(FilePutter file) {
			Session s = Session.get();
			if (s != null)
				s.scan(); // anchors of live gates only
			file.i(VERSION);
			file.i(HGROUP.all().size());
			int n = s == null ? 0 : s.store.size();
			file.i(n);
			if (s == null)
				return;
			for (Map.Entry<Integer, Session.Entry> e : s.store.entrySet()) {
				file.i(e.getKey());
				file.bool(e.getValue().inv);
				file.bool(e.getValue().bits != null);
				if (e.getValue().bits != null)
					file.is(e.getValue().bits);
			}
		}

		@Override
		public void load(FileGetter file) throws IOException {
			Session s = Session.get();
			int ver = file.i();
			if (ver != VERSION || s == null)
				return; // unknown layout: the game skips the rest by the saved size
			int groups = file.i();
			int n = file.i();
			int words = (groups + 31) >>> 5;
			boolean usable = groups == HGROUP.all().size(); // species list changed: read and drop
			s.store.clear();
			for (int i = 0; i < n; i++) {
				int anchor = file.i();
				boolean inv = file.bool();
				int[] bits = null;
				if (file.bool()) {
					bits = new int[words];
					file.is(bits);
				}
				if (usable)
					s.store.put(anchor, new Session.Entry(bits, inv));
			}
			s.dirty = true;
		}
	}
}
