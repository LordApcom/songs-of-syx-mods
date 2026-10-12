package lordapcom.wsf;

import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomInstance;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** Hard guard: every hiring path (allocator, swapper, simple employers) ends in this setter. */
@Mixin(target = "settlement.stats.colls.StatsWork$Work")
public final class WorkSetMixin {

	// game-src/settlement/stats/colls/StatsWork.java:397  public void set(Humanoid h, RoomInstance t)
	@Inject(method = "set", params = { Humanoid.class, RoomInstance.class }, at = At.HEAD, cancellable = true, require = true)
	public void guard(Object self, Cancellation ci, Humanoid h, RoomInstance t) {
		try {
			if (t != null && Filters.workHooksOn() && !Filters.mayWork(h, t))
				ci.cancel();
		} catch (Throwable e) {
			Filters.err("WorkSetMixin", e);
		}
	}
}
