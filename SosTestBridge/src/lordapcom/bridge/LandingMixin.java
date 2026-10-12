package lordapcom.bridge;

import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** Runs the bridge on the landing-party screen's frame update (game-src view/sett/SettViewStart.java:145). */
@Mixin(target = "view.sett.SettViewStart")
public final class LandingMixin {

	@Inject(method = "update", params = { float.class, boolean.class }, at = At.HEAD, require = true)
	public void tick(Object self, float ds, boolean should) {
		Bridge.tickScreen("landing", 0);
	}
}
