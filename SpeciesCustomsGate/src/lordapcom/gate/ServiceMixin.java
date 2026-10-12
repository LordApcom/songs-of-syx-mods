package lordapcom.gate;

import settlement.entity.humanoid.Humanoid;
import settlement.misc.util.FINDABLE;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/**
 * Plan-time decision for leisure targets (services, consumption): a service tile the asking humanoid could reach
 * but not come back from, because of the gate rules, is never offered, so the plan is never made.
 * Other SFINDER searches (stockpiles, resources, jobs) are left alone on purpose: filtering them stalls haulers.
 */
@Mixin(target = "settlement.path.finders.SFinderRoomService")
public final class ServiceMixin {

	// game-src/settlement/path/finders/SFinderRoomService.java:16  public FINDABLE getReservable(int tx, int ty)
	@ModifyReturn(method = "getReservable", params = { int.class, int.class }, require = true)
	public FINDABLE reservable(Object self, FINDABLE vanilla, int tx, int ty) {
		if (vanilla == null)
			return null;
		try {
			Session s = Session.get();
			if (s == null || !s.active)
				return vanilla;
			Humanoid who = Session.CUR.get();
			if (who == null)
				return vanilla;
			if (Session.avoids(who, tx, ty))
				return null;
			if (s.reach(who, tx, ty, true) == Session.Reach.DISCONNECTED)
				return null;
		} catch (Throwable t) {
			Session.err("reservable", t);
		}
		return vanilla;
	}
}
