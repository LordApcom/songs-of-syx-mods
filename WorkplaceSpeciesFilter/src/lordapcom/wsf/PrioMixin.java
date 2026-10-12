package lordapcom.wsf;

import init.type.WGROUP;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/** While the job allocator runs, a species gets priority 0 in a workplace type once the buildings that accept it are full (see Filters.effectivePrio). */
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
