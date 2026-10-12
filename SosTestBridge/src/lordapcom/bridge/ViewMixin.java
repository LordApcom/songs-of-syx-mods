package lordapcom.bridge;

import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/**
 * Fallback ticker on the view's frame update (game-src view/main/VIEW.java:189). The script update stops while a
 * popup (message, "knowledge low", load screen) holds the game; the bridge must keep answering then.
 */
@Mixin(target = "view.main.VIEW")
public final class ViewMixin {

	@Inject(method = "update", params = { float.class, double.class }, at = At.HEAD, require = true)
	public void tick(Object self, float ds, double slow) {
		Bridge.viewTick();
	}
}
