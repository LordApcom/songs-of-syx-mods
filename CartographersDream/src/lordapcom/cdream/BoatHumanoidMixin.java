package lordapcom.cdream;

import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** A passenger stands still in the boat: its own update (AI, movement) waits until it lands. */
@Mixin(target = "settlement.entity.humanoid.Humanoid")
public final class BoatHumanoidMixin {

	// game-src settlement/entity/humanoid/Humanoid.java:207 protected boolean update(double)
	@Inject(method = "update", params = { double.class }, at = At.HEAD, cancellable = true, require = true)
	public void update(Object self, Cancellation ci, double ds) {
		if (Boats.aboard(self))
			ci.cancel(true);
	}
}
