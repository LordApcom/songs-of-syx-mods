package lordapcom.wsf;

import init.sprite.UI.UI;
import init.sprite.SPRITES;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sprite.SPRITE;
import snake2d.util.sprite.text.Text;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import view.main.VIEW;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/**
 * Adds the species-filter entry to the bottom bar's Tools menu (the cog button), as one more cell of the tools grid.
 * The menu is the package-private view.sett.ui.bottom.Options panel, shown through Inter.set.
 */
@Mixin(target = "view.sett.ui.bottom.Inter")
public final class ToolsMenuMixin {

	private static Object done;

	// game-src/view/sett/ui/bottom/Inter.java:20  void set(CLICKABLE trigger, CLICKABLE panel)
	// Runs before Inter positions the panel above its trigger button (Inter.java:21-22), so growing it here is safe.
	@Inject(method = "set", params = { CLICKABLE.class, CLICKABLE.class }, at = At.HEAD, require = true)
	public void addEntry(Object self, CLICKABLE trigger, CLICKABLE panel) {
		try {
			if (done == panel || !"view.sett.ui.bottom.Options".equals(panel.getClass().getName()))
				return;
			done = panel;
			GuiSection s = (GuiSection) panel;
			RENDEROBJ last = s.elements().get(s.elements().size() - 1);
			int cellW = 350, cellH = 44; // BButt.WIDTH / HEIGHT (BButt.java:10-11); grid column pitch = 700/2 (Options.java:47-48)

			FilterTool tool = new FilterTool();
			// Same construction as BButt (BButt.java:15-20), which is package-private.
			GButt.ButtPanel b = new GButt.ButtPanel((SPRITE) new Text(UI.FONT().H2, "Species filter").setMaxWidth(cellW - 50).setMultipleLines(false)) {
				@Override
				protected void clickA() {
					VIEW.inters().popup.close();
					VIEW.s().tools.place(tool);
				}

				@Override
				public void hoverInfoGet(GUI_BOX text) {
					tool.hoverDesc((GBox) text);
				}
			};
			b.icon(new SPRITE.Wrap(SPRITES.icons().m.citizen, 32, 32));
			b.setDim(cellW, cellH);

			// Next free grid cell: right of the last entry if that one is in the left column, else a new row.
			int leftCX = Integer.MAX_VALUE, maxY2 = Integer.MIN_VALUE;
			for (int i = 0; i < s.elements().size(); i++) {
				RENDEROBJ e = s.elements().get(i);
				leftCX = Math.min(leftCX, e.body().cX());
				maxY2 = Math.max(maxY2, e.body().y2());
			}
			if (last.body().cX() == leftCX) {
				b.body().moveCX(leftCX + cellW);
				b.body().moveCY(last.body().cY());
			} else {
				b.body().moveCX(leftCX);
				b.body().moveY1(maxY2);
				s.body().incrH(8); // keep the bottom padding the menu had
			}
			s.add(b);
		} catch (Throwable e) {
			Filters.err("ToolsMenuMixin", e);
		}
	}
}
