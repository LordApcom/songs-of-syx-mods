package workplace.filter;

import java.util.Arrays;

import init.type.HGROUP;
import init.type.HGROUP.HTypeBitsImp;
import init.type.WGROUP;
import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import settlement.room.main.employment.RoomEmployment;

/**
 * Filter-aware capacity and instance selection shared by the shadowed vanilla
 * employment classes (Employer, RoomEmployments, RoomEmploymentSimple).
 *
 * Two rules, both derived from the per-instance worker filters in
 * {@link WorkplaceFilterManager}:
 *  1. {@link #fill}: a room type can only be allocated as many workers of a group as
 *     the instances ACCEPTING that group can hold (used by the Employer's allocator).
 *  2. {@link #pick}: when hiring, prefer the most restrictive instance that accepts
 *     the worker (a filtered room over an unfiltered one), so unfiltered capacity is
 *     left for the species nobody else accepts. Ties keep vanilla's rotating order.
 */
public final class FilterCaps {

	private FilterCaps() {}

	private static WorkplaceFilterManager mgr() {
		return WorkplaceFilterManager.INSTANCE;
	}

	/** HGROUP (species x class) that corresponds to a WGROUP, or null when none (then never filtered). */
	public static HGROUP hgroup(WGROUP w) {
		try {
			return HGROUP.get(w.type.CLASS, w.race);
		} catch (Exception e) {
			return null;
		}
	}

	/** Does instance {@code ins} accept group {@code w} under its filter (true when unfiltered)? */
	public static boolean accepts(RoomInstance ins, WGROUP w) {
		WorkplaceFilterManager m = mgr();
		if (m == null || !m.hasWorkerFilter(ins)) return true;
		HGROUP hg = hgroup(w);
		if (hg == null) return true;
		HTypeBitsImp bits = m.getWorkerFilter(ins);
		return bits == null || bits.is(hg);
	}

	/** Does instance {@code ins} accept humanoid {@code h} (true when unfiltered)? */
	public static boolean accepts(RoomInstance ins, Humanoid h) {
		WorkplaceFilterManager m = mgr();
		if (m != null) {
			try {
				if (!m.isWorkerAllowed(ins, h)) return false;
			} catch (Exception e) {
				// fail open on our own errors
			}
		}
		return reachableViaSpeciesCustomsGate(ins, h);
	}

	/** Cached reflection handle for the optional SpeciesCustomsGate mod's reachability API. */
	private static java.lang.reflect.Method scgCanReach;
	private static boolean scgResolved;

	/** SpeciesCustomsGate integration: never hire someone who can only reach the room through a gate that blocks them. */
	private static boolean reachableViaSpeciesCustomsGate(RoomInstance ins, Humanoid h) {
		try {
			if (!scgResolved) {
				scgResolved = true;
				try {
					Class<?> cls = Class.forName("customs.gate.GateReachability");
					scgCanReach = cls.getMethod("canReach", Humanoid.class, int.class, int.class);
				} catch (ClassNotFoundException ignore) {
					scgCanReach = null;
				}
			}
			if (scgCanReach == null) return true;
			Object r = scgCanReach.invoke(null, h, ins.mX(), ins.mY());
			return !(r instanceof Boolean) || ((Boolean) r);
		} catch (Throwable t) {
			return true;
		}
	}

	/**
	 * Fills {@code cap[g]} with the worker capacity of the active instances of {@code e}'s
	 * room type that accept group g. With no filters anywhere this is {@code needed} for
	 * every group (exactly what vanilla implicitly assumes).
	 */
	public static void fill(RoomEmployment e, int[] cap, int needed) {
		WorkplaceFilterManager m = mgr();
		if (m == null || !m.hasAnyWorkerFilter()) {
			Arrays.fill(cap, needed);
			return;
		}
		Arrays.fill(cap, 0);
		RoomBlueprintIns<?> bp = e.blueprint();
		int am = bp.instancesSize();
		boolean anyFiltered = false;
		for (int k = 0; k < am; k++) {
			RoomInstance ins = bp.getInstance(k);
			if (ins == null || !ins.active()) continue;
			int n = ins.employees().needed();
			if (n <= 0) continue;
			if (!m.hasWorkerFilter(ins)) {
				for (int gi = 0; gi < cap.length; gi++) cap[gi] += n;
				continue;
			}
			anyFiltered = true;
			for (WGROUP w : WGROUP.all()) {
				if (accepts(ins, w)) cap[w.index()] += n;
			}
		}
		if (!anyFiltered) {
			// no filtered instance on this room type: never be stricter than vanilla
			Arrays.fill(cap, needed);
		}
	}

	/** Number of HGROUPs a filtered instance accepts (lower = more restrictive); MAX for unfiltered. */
	private static int breadth(RoomInstance ins) {
		WorkplaceFilterManager m = mgr();
		if (m == null || !m.hasWorkerFilter(ins)) return Integer.MAX_VALUE;
		HTypeBitsImp bits = m.getWorkerFilter(ins);
		if (bits == null) return Integer.MAX_VALUE;
		int n = 0;
		for (int i = 0; i < HGROUP.all().size(); i++) if (bits.is(i)) n++;
		return n;
	}

	/**
	 * Picks the instance of {@code bp} to hire {@code h} at: active, has capacity, accepts h,
	 * most restrictive filter first, vanilla rotating order ({@code start}) on ties.
	 * Returns the instance INDEX, or -1 when no instance can take h.
	 */
	public static int pick(RoomBlueprintIns<?> bp, Humanoid h, int start) {
		int am = bp.instancesSize();
		if (am <= 0) return -1;
		int best = -1;
		int bestBreadth = Integer.MAX_VALUE;
		boolean filtersExist = mgr() != null && mgr().hasAnyWorkerFilter();
		for (int k = 0; k < am; k++) {
			int idx = (start + k) % am;
			RoomInstance ins = bp.getInstance(idx);
			if (ins == null || !ins.active()) continue;
			if (ins.employees().employed() >= ins.employees().target()) continue;
			if (!accepts(ins, h)) continue;
			if (!filtersExist) return idx; // vanilla path: first fit in rotating order
			int b = breadth(ins);
			if (best < 0 || b < bestBreadth) {
				best = idx;
				bestBreadth = b;
			}
		}
		return best;
	}

	/** True when some active instance of {@code bp} has free capacity (regardless of filters). */
	public static boolean anyCapacity(RoomBlueprintIns<?> bp) {
		int am = bp.instancesSize();
		for (int k = 0; k < am; k++) {
			RoomInstance ins = bp.getInstance(k);
			if (ins != null && ins.active() && ins.employees().employed() < ins.employees().target())
				return true;
		}
		return false;
	}
}
