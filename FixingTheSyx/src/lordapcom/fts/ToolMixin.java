package lordapcom.fts;

import snake2d.MButt;
import view.main.VIEW;
import view.subview.GameWindow;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/**
 * Per-frame driver. ToolPlacer.update runs every frame the placer tool is active (hovered or not), before
 * the vanilla sub-tool reads the mouse wheel, and keeps running while the game is paused.
 */
@Mixin(target = "view.tool.ToolPlacer")
public final class ToolMixin {

	// game-src/view/tool/ToolPlacer.java:235 (protected update, called from updateHovered too)
	@Inject(method = "update", params = {float.class, GameWindow.class}, at = At.HEAD, require = true)
	public void frame(Object self, float ds, GameWindow window) {
		try {
			if (self != VIEW.s().tools.placer)
				return;
			if (!MButt.LEFT.isDown())
				AreaTools.suppress = false;
			DecoRow.input(self);
			RoadMountain.keys(self);
			Pillars.tick();
		} catch (Throwable t) {
			Fts.err("tool frame", t);
		}
	}
}
