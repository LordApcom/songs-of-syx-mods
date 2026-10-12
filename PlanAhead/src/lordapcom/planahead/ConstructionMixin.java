package lordapcom.planahead;

import settlement.room.main.TmpArea;
import settlement.room.main.construction.ConstructionInit;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** Every room creation (placing, copy, saved prints) funnels through pcreate; locked ones are made dormant. */
@Mixin(target = "settlement.room.main.construction.CONSTRUCTION")
public final class ConstructionMixin {

	// game-src/settlement/room/main/construction/CONSTRUCTION.java:97  private void pcreate(TmpArea, ConstructionInit)
	@Inject(method = "pcreate", params = {TmpArea.class, ConstructionInit.class}, at = At.HEAD, require = true)
	public void before(Object self, TmpArea area, ConstructionInit init) {
		try {
			PlanAhead.beginCreate(init);
		} catch (Throwable e) {
			PlanAhead.warn(e);
		}
	}

	@Inject(method = "pcreate", params = {TmpArea.class, ConstructionInit.class}, at = At.RETURN, require = true)
	public void after(Object self, TmpArea area, ConstructionInit init) {
		try {
			PlanAhead.endCreate();
		} catch (Throwable e) {
			PlanAhead.warn(e);
		}
	}
}
