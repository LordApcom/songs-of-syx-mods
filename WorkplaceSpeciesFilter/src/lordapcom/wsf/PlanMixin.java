package lordapcom.wsf;

import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/**
 * Remembers which humanoid is updating, so the service finder knows who is asking. The whole update is covered, not
 * just plan choice: some service plans (latrines, for one) search for their room after the plan is chosen.
 */
@Mixin(target = "settlement.entity.humanoid.Humanoid")
public final class PlanMixin {

	// game-src/settlement/entity/humanoid/Humanoid.java:207  protected boolean update(double)
	@Inject(method = "update", params = { double.class }, at = At.HEAD, require = true)
	public void enter(Object self, double ds) {
		if (Filters.anyU)
			Filters.REQ.set((settlement.entity.humanoid.Humanoid) self);
		else if (Filters.REQ.get() != null)
			Filters.REQ.remove();
	}

	@Inject(method = "update", params = { double.class }, at = At.RETURN, require = true)
	public void leave(Object self, double ds) {
		if (Filters.REQ.get() != null)
			Filters.REQ.remove();
	}
}
