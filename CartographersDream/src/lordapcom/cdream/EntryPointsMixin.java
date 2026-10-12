package lordapcom.cdream;

import settlement.entry.EntryPoints;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.sets.ArrayList;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/** While a sea-locked capital is playable, outsiders arrive at a dock instead of a map-edge road. */
@Mixin(EntryPoints.class)
public final class EntryPointsMixin {

	// game-src settlement/entry/EntryPoints.java:350 update(): "reachable" decides Capital Closed and immigration
	@Inject(method = "update", params = {}, at = At.RETURN, require = true)
	@SuppressWarnings("unchecked")
	public void keepOpenWhenSeaLocked(Object self) {
		try {
			if (!Island.locked)
				return;
			EntryPoints p = (EntryPoints) self;
			ArrayList<EntryPoints.EntryPoint> reachable = (ArrayList<EntryPoints.EntryPoint>) p.reachable();
			if (reachable.size() == 0 && p.all().size() > 0)
				reachable.add(p.all().get(0));
		} catch (Throwable t) {
			Log.once("EntryPointsMixin.update", t);
		}
	}

	// game-src settlement/entry/EntryPoints.java:308 randomReachable(int); the no-arg overload calls it
	@ModifyReturn(method = "randomReachable", params = { int.class }, require = true)
	public COORDINATE dockInstead(Object self, COORDINATE vanilla, int rr) {
		try {
			if (Island.locked) {
				COORDINATE d = Island.randomDock(rr);
				if (d != null)
					Boats.arm(d.x(), d.y());
				return d;
			}
		} catch (Throwable t) {
			Log.once("EntryPointsMixin.randomReachable", t);
		}
		return vanilla;
	}
}
