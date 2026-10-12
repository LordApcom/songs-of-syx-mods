package lordapcom.gate;

import init.constant.C;
import init.race.RACES;
import init.race.Race;
import init.sprite.SPRITES;
import init.sprite.UI.Icon;
import init.type.HCLASS;
import init.type.HCLASSES;
import init.type.HGROUP;
import snake2d.SPRITE_RENDERER;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.sprite.SPRITE;
import util.gui.misc.GButt;
import util.gui.panel.GPanel;
import view.main.VIEW;

/** The per-gate filter panel: species x class grid, four presets, Invert arrow, Apply. */
final class GatePanel {

	private static final int PER_ROW = 8;

	private GatePanel() {
	}

	static void open(Session s, Session.Gate g) {
		int n = HGROUP.all().size();
		boolean[] sel = new boolean[n];
		for (HGROUP h : Lists.of(HGROUP.all()))
			sel[h.index] = g.allows(h);
		boolean[] inv = { g.inv };

		GuiSection c = new GuiSection();

		GuiSection quick = new GuiSection();
		quick.addRightC(8, preset("Allow all", "Every species and class may pass.", sel, null, true));
		quick.addRightC(8, preset("Citizens only", "Only citizens may pass.", sel, HCLASSES.CITIZEN(), false));
		quick.addRightC(8, preset("Slaves only", "Only slaves may pass.", sel, HCLASSES.SLAVE(), false));
		quick.addRightC(8, preset("Block all", "Nobody may pass, except with the arrow.", sel, null, false));
		c.add(quick);

		GuiSection grid = new GuiSection();
		int rows = 0;
		for (HCLASS cl : new HCLASS[] { HCLASSES.CITIZEN(), HCLASSES.SLAVE() }) {
			int i = 0;
			for (Race r : Lists.of(RACES.all())) {
				HGROUP h = HGROUP.get(cl, r);
				if (h == null)
					continue;
				CLICKABLE b = new GButt.ButtPanel(h.icon) {
					@Override
					protected void clickA() {
						sel[h.index] = !sel[h.index];
					}

					@Override
					protected void renAction() {
						selectedSet(sel[h.index]);
					}
				}.hoverInfoSet("Allow: " + h.name);
				int w = b.body().width() + 4, hh = b.body().height() + 4;
				grid.add(b, (i % PER_ROW) * w, (rows + i / PER_ROW) * hh);
				i++;
			}
			rows += (i + PER_ROW - 1) / PER_ROW;
		}
		c.addRelBody(8, DIR.S, grid);

		GuiSection foot = new GuiSection();
		SPRITE arrow = new SPRITE.Imp(Icon.M, Icon.M) {
			@Override
			public void render(SPRITE_RENDERER r, int X1, int X2, int Y1, int Y2) {
				arrowIcon(g.axisY, inv[0] ? -1 : 1).render(r, X1, X2, Y1, Y2);
			}
		};
		foot.addRightC(0, new GButt.ButtPanel("Invert arrow") {
			@Override
			protected void clickA() {
				inv[0] = !inv[0];
			}
		}.icon(arrow).hoverInfoSet("Not-allowed species may only pass in the arrow's direction."));
		foot.addRightC(16, new GButt.ButtPanel("Apply") {
			@Override
			protected void clickA() {
				s.edit(g, Session.normalize(sel), inv[0]);
				VIEW.s().interrupters.section.deactivate();
			}
		}.hoverInfoSet("Save this gate's filter."));
		c.addRelBody(8, DIR.S, foot);

		c.pad(8 * C.SG);
		GPanel box = new GPanel();
		box.setTitle("Customs gate");
		box.inner().set(c.body());
		GuiSection root = new GuiSection();
		root.add(box);
		root.add(c);
		root.body().centerIn(C.DIM());
		VIEW.s().interrupters.section.activate(root);
	}

	/** only == null: all or nothing by 'all'; otherwise exactly that class. */
	private static CLICKABLE preset(String label, String hover, boolean[] sel, HCLASS only, boolean all) {
		return new GButt.ButtPanel(label) {
			@Override
			protected void clickA() {
				for (HGROUP h : Lists.of(HGROUP.all()))
					sel[h.index] = only == null ? all : h.type == only;
			}
		}.hoverInfoSet(hover);
	}

	static SPRITE arrowIcon(boolean axisY, int dir) {
		if (axisY)
			return dir > 0 ? SPRITES.icons().m.arrow_down : SPRITES.icons().m.arrow_up;
		return dir > 0 ? SPRITES.icons().m.arrow_right : SPRITES.icons().m.arrow_left;
	}
}
