package lordapcom.planahead;

import settlement.job.Job;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** The Activate tool wakes the building's jobs before toggling the room; stop that for locked buildings. */
@Mixin(target = "settlement.job.StateManager")
public final class JobStateMixin {

	// game-src/settlement/job/StateManager.java:182  public void activate(int tile, Job job)
	@Inject(method = "activate", params = {int.class, Job.class}, at = At.HEAD, cancellable = true, require = true)
	public void guard(Object self, Cancellation ci, int tile, Job job) {
		try {
			if (PlanAhead.blockedTile(tile))
				ci.cancel();
		} catch (Throwable e) {
			PlanAhead.warn(e);
		}
	}
}
