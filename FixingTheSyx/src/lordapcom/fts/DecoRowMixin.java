package lordapcom.fts;

import snake2d.SPRITE_RENDERER;
import view.subview.GameWindow;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** Feature 2 hooks on the fixed-size placer tool (single items such as torches and statues). */
@Mixin(target = "view.tool.PlacableFixedTool")
public final class DecoRowMixin {

	// game-src/view/tool/PlacableFixedTool.java:167 (click is also called every frame while the button is held)
	@Inject(method = "click", params = {GameWindow.class}, at = At.HEAD, cancellable = true, require = true)
	public void click(Object self, Cancellation ci, GameWindow window) {
		try {
			if (DecoRow.place(self, window))
				ci.cancel();
		} catch (Throwable t) {
			Fts.err("deco click", t);
		}
	}

	// game-src/view/tool/PlacableFixedTool.java:100
	@Inject(method = "render", params = {SPRITE_RENDERER.class, float.class, GameWindow.class}, at = At.TAIL, require = true)
	public void render(Object self, SPRITE_RENDERER r, float ds, GameWindow window) {
		try {
			DecoRow.ghosts(self, r, window);
		} catch (Throwable t) {
			Fts.err("deco render", t);
		}
	}
}
