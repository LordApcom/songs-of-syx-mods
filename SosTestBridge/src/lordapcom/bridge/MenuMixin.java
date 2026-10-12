package lordapcom.bridge;

import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** Runs the bridge on the main menu's frame update (game-src menu/Menu.java:125). */
@Mixin(target = "menu.Menu")
public final class MenuMixin {

	@Inject(method = "update", params = { float.class, double.class }, at = At.HEAD, require = true)
	public void tick(Object self, float ds, double slow) {
		Flow.menu = self;
		Bridge.tickScreen("menu", 0);
	}
}
