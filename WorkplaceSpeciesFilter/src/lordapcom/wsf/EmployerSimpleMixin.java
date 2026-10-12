package lordapcom.wsf;

import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomInstance;
import settlement.room.main.employment.RoomEmploymentSimple;
import settlement.stats.STATS;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;
import vizardalpha.polyphon.api.Shadow;

/** Guards, trainers, universities, rest homes: if the setter refused the hire, report "not employed". */
@Mixin(target = "settlement.room.main.employment.RoomEmploymentSimple$EmployerSimple")
public final class EmployerSimpleMixin {

	// game-src/settlement/room/main/employment/RoomEmploymentSimple.java:251  public boolean employ(Humanoid h)
	@ModifyReturn(method = "employ", params = { Humanoid.class }, require = true)
	public boolean employ(Object self, boolean vanilla, Humanoid h) {
		try {
			if (!vanilla || !Filters.workHooksOn())
				return vanilla;
			RoomEmploymentSimple si = Shadow.get(self, "si");
			RoomInstance ins = STATS.WORK().EMPLOYED.get(h);
			return ins != null && ins.blueprintI() == si.blueprint();
		} catch (Throwable e) {
			Filters.err("EmployerSimpleMixin", e);
			return vanilla;
		}
	}
}
