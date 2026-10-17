package lordapcom.practice;

import java.lang.reflect.Field;

import snake2d.util.misc.ACTION;
import snake2d.util.sets.LinkedList;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/**
 * Once per game setup. Tech boosts are read into the techs by the game's "waiting" actions and wired into the
 * boostables by its "connecter" actions, both inside finishSetup. Queuing last in "waiting" runs Skills.setup between
 * the two.
 */
@Mixin(target = "game.boosting.BOOSTING")
public final class BoostingMixin {

	// game-src/game/boosting/BOOSTING.java:53  protected void finishSetup()
	@Inject(method = "finishSetup", params = {}, at = At.HEAD, require = true)
	@SuppressWarnings("unchecked")
	public void setup(Object self) {
		try {
			Field f = Class.forName("game.boosting.BOOSTING").getDeclaredField("waiting");
			f.setAccessible(true);
			((LinkedList<ACTION>) f.get(null)).add(() -> {
				try {
					Skills.setup();
				} catch (Throwable t) {
					Skills.err("setup", t);
				}
			});
		} catch (Throwable t) {
			Skills.err("queue setup", t);
		}
	}
}
