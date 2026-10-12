package lordapcom.fts;

import view.subview.GameWindow;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/**
 * Features 3 and 5: Ctrl+click shortcuts on the area/drag placer tool. When a shortcut handles the click,
 * the tool's own place() is cancelled until the mouse button is released.
 */
@Mixin(target = "view.tool.PlacableMultiTool")
public final class MultiToolMixin {

	// game-src/view/tool/PlacableMultiTool.java:272
	@Inject(method = "click", params = {GameWindow.class}, at = At.HEAD, require = true)
	public void click(Object self, GameWindow window) {
		try {
			AreaTools.suppress = AreaTools.onClick(self, window);
		} catch (Throwable t) {
			AreaTools.suppress = false;
			Fts.err("area click", t);
		}
	}

	// game-src/view/tool/PlacableMultiTool.java:312 (private place(); called from click, clickRelease, updateHovered)
	@Inject(method = "place", params = {}, at = At.HEAD, cancellable = true, require = true)
	public void place(Object self, Cancellation ci) {
		if (AreaTools.suppress)
			ci.cancel();
	}

	// game-src/view/tool/PlacableMultiTool.java:306
	@Inject(method = "clickRelease", params = {GameWindow.class}, at = At.TAIL, require = true)
	public void release(Object self, GameWindow window) {
		AreaTools.suppress = false;
	}
}
