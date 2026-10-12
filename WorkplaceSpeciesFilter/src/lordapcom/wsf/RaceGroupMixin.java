package lordapcom.wsf;

import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomInstance;
import settlement.room.main.employment.RoomEmployment;
import settlement.stats.STATS;
import snake2d.util.sets.IntegerStack;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.Shadow;

/**
 * Vanilla picks the next instance with a free slot and keeps retrying that workplace type if the setter does
 * nothing, which would starve the species. This picks only instances that accept the humanoid.
 */
@Mixin(target = "settlement.room.main.employment.RoomEmployments$RaceGroup")
public final class RaceGroupMixin {

	// game-src/settlement/room/main/employment/RoomEmployments.java:362  private boolean setWork(Humanoid i, RoomEmployment e, int[] searchI)
	@Inject(method = "setWork", params = { Humanoid.class, RoomEmployment.class, int[].class }, at = At.HEAD, cancellable = true, require = true)
	public void pick(Object self, Cancellation ci, Humanoid i, RoomEmployment e, int[] searchI) {
		try {
			if (!Filters.workHooksOn())
				return;
			int am = e.blueprint().instancesSize();
			int ei = e.index();
			for (int k = 0; k < am; k++) {
				if (searchI[ei] >= am)
					searchI[ei] = 0;
				RoomInstance ins = e.blueprint().getInstance(searchI[ei]);
				if (ins.active() && ins.employees().employed() < ins.employees().target() && Filters.mayWork(i, ins)) {
					STATS.WORK().EMPLOYED.set(i, ins);
					ci.cancel(true);
					return;
				}
				searchI[ei]++;
			}
			// Nothing for this humanoid here: drop the workplace type from the group's stack (the caller just
			// pushed it back) so the next one gets a turn. The game rebuilds the stack every few ticks.
			IntegerStack st = Shadow.get(self, "possibles");
			if (!st.isEmpty()) {
				int top = st.pop();
				if (top != ei)
					st.push(top);
			}
			ci.cancel(false);
		} catch (Throwable t) {
			Filters.err("RaceGroupMixin", t);
		}
	}
}
