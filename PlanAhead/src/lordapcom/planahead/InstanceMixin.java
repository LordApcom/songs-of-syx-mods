package lordapcom.planahead;

import settlement.room.main.Room;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** Refuses to switch a locked building out of planning. */
@Mixin(target = "settlement.room.main.construction.ConstructionInstance")
public final class InstanceMixin {

	// game-src/settlement/room/main/construction/ConstructionInstance.java:997  public void jobToggle(boolean)
	@Inject(method = "jobToggle", params = {boolean.class}, at = At.HEAD, cancellable = true, require = true)
	public void guard(Object self, Cancellation ci, boolean toggle) {
		try {
			if (toggle && PlanAhead.blockedRoom((Room) self))
				ci.cancel();
		} catch (Throwable e) {
			PlanAhead.warn(e);
		}
	}
}
