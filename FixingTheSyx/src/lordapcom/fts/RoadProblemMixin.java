package lordapcom.fts;

import view.tool.PlacableMessages;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/** Feature 4: lets the road tool accept mountain tiles (previewed as placeable, i.e. green). */
@Mixin(target = "settlement.job.JobBuildRoad")
public final class RoadProblemMixin {

	// game-src/settlement/job/JobBuildRoad.java:209 (every road type is an instance of this class)
	@ModifyReturn(method = "problem", params = {int.class, int.class, boolean.class}, require = true)
	public CharSequence mountainOk(Object self, CharSequence vanilla, int tx, int ty, boolean overwrite) {
		try {
			if (vanilla == PlacableMessages.¤¤BLOCKED && RoadMountain.canQueue(tx, ty))
				return null;
		} catch (Throwable t) {
			Fts.err("road problem", t);
		}
		return vanilla;
	}
}
