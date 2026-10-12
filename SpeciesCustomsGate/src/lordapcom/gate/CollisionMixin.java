package lordapcom.gate;

import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/** Makes a gate tile solid for exactly the humanoids the gate filter rejects (physical rule, per entity). */
@Mixin(target = "settlement.entity.ResolverTile")
public final class CollisionMixin {

	// game-src/settlement/entity/ResolverTile.java:222  private static boolean collides(int tx, int ty, ENTITY e)
	@ModifyReturn(method = "collides", params = { int.class, int.class, ENTITY.class }, require = true)
	public boolean collides(Object self, boolean vanilla, int tx, int ty, ENTITY e) {
		if (vanilla || !(e instanceof Humanoid))
			return vanilla;
		try {
			Session s = Session.get();
			return s != null && s.active && s.blocks((Humanoid) e, tx, ty);
		} catch (Throwable t) {
			Session.err("collides", t);
			return vanilla;
		}
	}
}
