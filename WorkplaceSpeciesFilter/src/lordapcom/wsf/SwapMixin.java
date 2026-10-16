package lordapcom.wsf;

import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomInstance;
import settlement.stats.STATS;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;

/**
 * Vanilla swaps two workers between buildings of a type with two setter calls. When the filter refuses one of them,
 * only half the swap happens: a worker leaves the filtered building and the other one is interrupted for nothing,
 * over and over, so the buildings run half empty. Swaps are skipped in workplace types that have a worker filter.
 * ponytail: also skips the swaps the filter would allow; check each pair if players miss them.
 */
@Mixin(target = "settlement.entity.humanoid.ai.work.AIModule_Work$Swapper")
public final class SwapMixin {

	// game-src/settlement/entity/humanoid/ai/work/AIModule_Work.java:290  private void swap(Humanoid h)
	@Inject(method = "swap", params = { Humanoid.class }, at = At.HEAD, cancellable = true, require = true)
	public void swap(Object self, Cancellation ci, Humanoid h) {
		try {
			if (!Filters.anyW)
				return;
			RoomInstance w = STATS.WORK().EMPLOYED.get(h);
			if (w != null && Filters.typeFiltered(w))
				ci.cancel();
		} catch (Throwable t) {
			Filters.err("SwapMixin", t);
		}
	}
}
