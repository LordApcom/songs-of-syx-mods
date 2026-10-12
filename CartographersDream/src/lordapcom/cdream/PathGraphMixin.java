package lordapcom.cdream;

import snake2d.util.misc.ACTION;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/** After the world path graph is generated, links an isolated capital to the nearest regions. */
@Mixin(target = "world.map.pathing.Gen")
public final class PathGraphMixin {

	// game-src world/map/pathing/Gen.java:20 generateAll(int,int,ACTION) (package-private class)
	@Inject(method = "generateAll", params = { int.class, int.class, ACTION.class }, at = At.RETURN, require = true)
	public void linkCapital(Object self, int px, int py, ACTION step) {
		try {
			WorldLink.ensure(px, py);
		} catch (Throwable t) {
			Log.once("PathGraphMixin", t);
		}
	}
}
