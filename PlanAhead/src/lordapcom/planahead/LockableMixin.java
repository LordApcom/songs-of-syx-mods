package lordapcom.planahead;

import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/** Makes plannable room locks read as unlocked for the build/copy UI only (see PlanAhead.UI). */
@Mixin(target = "init.value.Lockable")
public final class LockableMixin {

	// game-src/init/value/Lockable.java:45  public boolean passes(T t)  (erased to Object)
	@ModifyReturn(method = "passes", params = {Object.class}, require = true)
	public boolean passes(Object self, boolean vanilla, Object t) {
		if (vanilla)
			return true;
		try {
			return PlanAhead.bypass(self);
		} catch (Throwable e) {
			PlanAhead.warn(e);
			return false;
		}
	}
}
