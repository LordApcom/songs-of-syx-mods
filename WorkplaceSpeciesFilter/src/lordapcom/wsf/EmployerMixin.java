package lordapcom.wsf;

import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** Marks the job allocator's run (so PrioMixin only acts there) and trims slots the filters make unusable. */
@Mixin(target = "settlement.room.main.employment.Employer")
public final class EmployerMixin {

	// game-src/settlement/room/main/employment/Employer.java:52  void update()
	@Inject(method = "update", params = {}, at = At.HEAD, require = true)
	public void enter(Object self) {
		Filters.allocator = self;
	}

	@Inject(method = "update", params = {}, at = At.RETURN, require = true)
	public void leave(Object self) {
		try {
			Filters.trim(self);
		} catch (Throwable e) {
			Filters.err("EmployerMixin", e);
		} finally {
			Filters.allocator = null;
		}
	}
}
