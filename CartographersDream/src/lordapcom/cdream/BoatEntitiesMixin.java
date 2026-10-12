package lordapcom.cdream;

import settlement.entity.ENTITY;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/** Every humanoid that enters the world passes here; arrivals at a sea-locked capital are put on a boat. */
@Mixin(target = "settlement.entity.ENTETIES")
public final class BoatEntitiesMixin {

	// game-src settlement/entity/ENTETIES.java:189 boolean add(ENTITY, boolean)
	@ModifyReturn(method = "add", params = { ENTITY.class, boolean.class }, require = true)
	public boolean added(Object self, boolean vanilla, ENTITY e, boolean collide) {
		try {
			if (vanilla)
				Boats.added(e);
		} catch (Throwable t) {
			Log.once("BoatEntitiesMixin.add", t);
		}
		return vanilla;
	}
}
