package lordapcom.fts;

import settlement.job.Job;
import settlement.job.JobBuildRoad;
import snake2d.util.datatypes.AREA;
import view.tool.PLACER_TYPE;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.Shadow;

/** Feature 4: a road "placed" on rock becomes dig-then-road. The tool only calls place() on release. */
@Mixin(target = "settlement.job.Placer")
public final class RoadPlaceMixin {

	// game-src/settlement/job/Placer.java:60 (superclass of every job placer)
	@Inject(method = "place", params = {int.class, int.class, AREA.class, PLACER_TYPE.class}, at = At.HEAD, cancellable = true, require = true)
	public void place(Object self, Cancellation ci, int tx, int ty, AREA a, PLACER_TYPE t) {
		try {
			if (!RoadMountain.canQueue(tx, ty))
				return;
			Object j = Shadow.get(self, "j");
			if (j instanceof JobBuildRoad) {
				RoadMountain.queue(tx, ty, (Job) j);
				ci.cancel();
			}
		} catch (Throwable e) {
			Fts.err("road place", e);
		}
	}
}
