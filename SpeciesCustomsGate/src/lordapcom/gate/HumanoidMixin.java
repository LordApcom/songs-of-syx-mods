package lordapcom.gate;

import settlement.entity.humanoid.Humanoid;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** Stuck safety net input (gate bumps) and per-humanoid cleanup. */
@Mixin(target = "settlement.entity.humanoid.Humanoid")
public final class HumanoidMixin {

	// game-src/settlement/entity/humanoid/Humanoid.java:476  public boolean collideTile(boolean, double, double, double, int, int)
	// Only runs when the resolver pushed the humanoid out of a tile, so a gate hit here means the filter denied it.
	@Inject(method = "collideTile", params = { boolean.class, double.class, double.class, double.class, int.class,
			int.class }, at = At.HEAD, require = true)
	public void bumped(Object self, boolean broken, double nx, double ny, double force, int tx, int ty) {
		try {
			Session s = Session.get();
			if (s != null && s.active && s.blocks((Humanoid) self, tx, ty))
				s.bump((Humanoid) self);
		} catch (Throwable t) {
			Session.err("bumped", t);
		}
	}

	// game-src/settlement/entity/humanoid/Humanoid.java:495  protected void removeAction()
	@Inject(method = "removeAction", params = {}, at = At.HEAD, require = true)
	public void removed(Object self) {
		try {
			Session.H.remove(self);
		} catch (Throwable t) {
			Session.err("removed", t);
		}
	}
}
