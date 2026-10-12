package lordapcom.wsf;

import init.type.WGROUP;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/** Reports priority 0 for species that no instance of a workplace type accepts, so slots are not wasted on them. */
@Mixin(target = "settlement.room.main.employment.RoomEmployment$GRoupInt")
public final class PrioMixin {

	// game-src/settlement/room/main/employment/RoomEmployment.java:234  public int get(WGROUP t)
	@ModifyReturn(method = "get", params = { WGROUP.class }, require = true)
	public int prio(Object self, int vanilla, WGROUP g) {
		try {
			return Filters.effectivePrio(self, vanilla, g);
		} catch (Throwable e) {
			Filters.err("PrioMixin", e);
			return vanilla;
		}
	}
}
