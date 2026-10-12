package lordapcom.cdream;

import settlement.thing.halfEntity.HalfEnts;
import snake2d.Renderer;
import snake2d.util.datatypes.RECTANGLE;
import util.rendering.ShadowBatch;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** Draws the arrival boats in the same pass as the game's fishing boats. */
@Mixin(HalfEnts.class)
public final class BoatRenderMixin {

	// game-src settlement/thing/halfEntity/HalfEnts.java:190 renderBelow(Renderer, ShadowBatch, float, RECTANGLE, int, int)
	@Inject(method = "renderBelow", params = { Renderer.class, ShadowBatch.class, float.class, RECTANGLE.class, int.class, int.class }, at = At.HEAD, require = true)
	public void render(Object self, Renderer r, ShadowBatch s, float ds, RECTANGLE win, int offX, int offY) {
		try {
			Boats.render(r, s, ds, win, offX, offY);
		} catch (Throwable t) {
			Log.once("BoatRenderMixin", t);
		}
	}
}
