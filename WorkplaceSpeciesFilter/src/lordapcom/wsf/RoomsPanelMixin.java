package lordapcom.wsf;

import init.sprite.SPRITES;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sprite.SPRITE;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import view.interrupter.ISidePanel;
import view.main.VIEW;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.Shadow;

/**
 * Second way in: a "Species filter" button on the rooms panel ("workforce & rooms", top bar), right of "Priority".
 * The panel is rebuilt for every new map (UIRooms.java:57-63), so the button is added once per panel instance.
 */
@Mixin(target = "view.sett.ui.room.UIRooms")
public final class RoomsPanelMixin {

	private static Object done;

	// game-src/view/sett/ui/room/UIRooms.java:83  public ISidePanel main(); the top bar calls it every frame (UIPanelTopSett.java:167)
	@Inject(method = "main", params = {}, at = At.HEAD, require = true)
	public void addButton(Object self) {
		try {
			ISidePanel main = Shadow.get(self, "main");
			if (main == null || main == done)
				return;
			done = main;
			// First row of UIPanelMain (UIPanelMain.java:71-110): the employed-count button and "Priority".
			for (RENDEROBJ e : Lists.of(main.section().elements())) {
				if (!(e instanceof GuiSection row) || row.elements().size() != 2
						|| !row.elements().get(1).getClass().getName().startsWith("view.sett.ui.room.UIPanelMain$"))
					continue;
				GButt.ButtPanel b = new GButt.ButtPanel(new SPRITE.Wrap(SPRITES.icons().m.citizen, 24, 24)) {
					@Override
					protected void clickA() {
						VIEW.s().tools.place(FilterTool.shared());
					}

					@Override
					public void hoverInfoGet(GUI_BOX text) {
						FilterTool.shared().hoverDesc((GBox) text);
					}
				};
				b.setDim(row.elements().get(1).body().height(), row.elements().get(1).body().height());
				row.addRightC(0, b);
				return;
			}
			Filters.err("RoomsPanelMixin", new IllegalStateException("rooms panel row not found"));
		} catch (Throwable t) {
			Filters.err("RoomsPanelMixin", t);
		}
	}
}
