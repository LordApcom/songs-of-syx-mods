package lordapcom.gate;

import settlement.entity.humanoid.Humanoid;

/** Cross-mod entry point. WorkplaceSpeciesFilter calls canReach by reflection. */
public final class GateApi {

	private GateApi() {
	}

	/**
	 * False only when the tile is reachable without gates but proven unreachable for this humanoid with them
	 * (one-way is enough). True when the mod is inactive, the humanoid is hostile or a tourist, the species is
	 * allowed at every gate, the check ran out of budget, or anything failed.
	 */
	public static boolean canReach(Humanoid h, int tx, int ty) {
		try {
			Session s = Session.get();
			return s == null || s.reach(h, tx, ty, false) != Session.Reach.DISCONNECTED;
		} catch (Throwable t) {
			Session.err("canReach", t);
			return true;
		}
	}
}
