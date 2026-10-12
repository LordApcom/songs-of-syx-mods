package lordapcom.cdream;

import settlement.thing.halfEntity.caravan.Caravan;
import snake2d.Renderer;
import util.rendering.ShadowBatch;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/** Merchant caravans arriving at a sea-locked capital wait (hidden) while a boat brings their goods to the shore. */
@Mixin(Caravan.class)
public final class BoatCaravanMixin {

	// game-src settlement/thing/halfEntity/caravan/Caravan.java:113 boolean init(int, int, Type, RESOURCE, int, TRADE_TYPE)
	@ModifyReturn(method = "init", descriptor = "(IILsettlement/thing/halfEntity/caravan/Type;Linit/resources/RESOURCE;ILinit/trade/TRADE_TYPE;)Z", require = true)
	public boolean created(Object self, boolean vanilla, int tx, int ty, Object type, Object res, int amount, Object ttype) {
		try {
			if (vanilla)
				Boats.caravan((Caravan) self);
		} catch (Throwable t) {
			Log.once("BoatCaravanMixin.init", t);
		}
		return vanilla;
	}

	// game-src settlement/thing/halfEntity/caravan/Caravan.java:134 protected void update(double)
	@Inject(method = "update", params = { double.class }, at = At.HEAD, cancellable = true, require = true)
	public void update(Object self, Cancellation ci, double ds) {
		if (Boats.aboard(self))
			ci.cancel();
	}

	// game-src settlement/thing/halfEntity/caravan/Caravan.java:177 public void render(Renderer, ShadowBatch, float, int, int)
	@Inject(method = "render", params = { Renderer.class, ShadowBatch.class, float.class, int.class, int.class }, at = At.HEAD, cancellable = true, require = true)
	public void render(Object self, Cancellation ci, Renderer r, ShadowBatch s, float ds, int x, int y) {
		if (Boats.aboard(self))
			ci.cancel();
	}
}
