package lordapcom.gate;

import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.ai.main.AIManager;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** Tells the service finder who is choosing a plan, and forgets stale bump counts when a new plan starts. */
@Mixin(target = "settlement.entity.humanoid.ai.main.AIModules")
public final class PlanMixin {

	// game-src/settlement/entity/humanoid/ai/main/AIModules.java:204  AiPlanActivation getNextPlan(Humanoid a, AIManager d)
	@Inject(method = "getNextPlan", params = { Humanoid.class, AIManager.class }, at = At.HEAD, require = true)
	public void enter(Object self, Humanoid a, AIManager d) {
		try {
			Session s = Session.get();
			if (s == null || !s.active)
				return;
			Session.CUR.set(a);
			Session.HState st = Session.H.get(a);
			if (st != null)
				st.bumps = 0;
		} catch (Throwable t) {
			Session.err("plan enter", t);
		}
	}

	@Inject(method = "getNextPlan", params = { Humanoid.class, AIManager.class }, at = At.RETURN, require = true)
	public void leave(Object self, Humanoid a, AIManager d) {
		Session.CUR.remove();
	}
}
