package lordapcom.bridge;

import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** Runs the bridge on the world generator's frame update (game-src view/world/generator/WorldViewGenerator.java:118). */
@Mixin(target = "view.world.generator.WorldViewGenerator")
public final class WorldGenMixin {

	@Inject(method = "update", params = { float.class, boolean.class }, at = At.HEAD, require = true)
	public void tick(Object self, float ds, boolean should) {
		Flow.gen = self;
		Bridge.tickScreen("worldgen", 0);
	}
}
