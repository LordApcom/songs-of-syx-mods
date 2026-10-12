package lordapcom.planahead;

import java.util.IdentityHashMap;

import game.faction.FACTIONS;
import settlement.main.SETT;
import settlement.room.main.Room;
import settlement.room.main.RoomBlueprint;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.construction.ConstructionInit;
import settlement.room.main.furnisher.Furnisher;
import snake2d.LOG;

/** Shared logic for the Plan Ahead mixins. No per-save state lives here. */
public final class PlanAhead {

	private PlanAhead() {}

	/** Callers (by class-name prefix) that may see locked-but-plannable rooms as unlocked. */
	private static final String[] UI = {
		"view.sett.ui.bottom.BuildMain",              // build menu + decoration buttons
		"view.sett.ui.room.construction.SCollection", // room variant picker
		"view.sett.ui.room.prints.",                  // saved blueprints (list + placing)
		"settlement.room.main.copy.Copier",           // Copier + CopierMass (copy/paste of rooms)
		"settlement.room.main.copy.BSwap",            // copy tool: keep the planned building type
		"view.sett.ToolDefault",                      // MOD+click on a room to build another
	};

	private static final StackWalker WALKER = StackWalker.getInstance();
	private static IdentityHashMap<Object, Boolean> plannable;
	private static boolean inside;
	private static boolean forced;
	private static boolean warned;

	static void warn(Throwable t) {
		if (warned)
			return;
		warned = true;
		LOG.ln("[PlanAhead] " + t);
	}

	/** Called on game/save load so the lock table is rebuilt. */
	static void reset() {
		plannable = null;
		endCreate();
	}

	/** Real lock check, immune to our own bypass. */
	static boolean locked(RoomBlueprintImp b) {
		inside = true;
		try {
			return !b.reqs.passes(FACTIONS.player());
		} finally {
			inside = false;
		}
	}

	/**
	 * Rooms that go through the construction/planning pipeline. Free, outdoor, unfloored rooms
	 * are built instantly by the game (no planning step), so they must stay locked.
	 */
	private static IdentityHashMap<Object, Boolean> plannable() {
		if (plannable == null) {
			IdentityHashMap<Object, Boolean> m = new IdentityHashMap<>();
			for (RoomBlueprint p : Lists.of(SETT.ROOMS().all())) {
				if (!(p instanceof RoomBlueprintImp) || p == SETT.ROOMS().THRONE)
					continue;
				RoomBlueprintImp b = (RoomBlueprintImp) p;
				try {
					Furnisher f = b.constructor();
					if (f != null && !(f.resources() == 0 && !f.mustBeIndoors() && !f.needFlooring()))
						m.put(b.reqs, Boolean.TRUE);
				} catch (Throwable t) {
					warn(t);
				}
			}
			plannable = m;
		}
		return plannable;
	}

	/** Should Lockable.passes() answer true for this caller? */
	static boolean bypass(Object lockable) {
		if (inside || !plannable().containsKey(lockable))
			return false;
		String c = WALKER.walk(s -> s.map(StackWalker.StackFrame::getClassName)
			.filter(n -> !n.startsWith("init.value.Lockable") && !n.startsWith("vizardalpha.")
				&& !n.startsWith("lordapcom.planahead") && !n.startsWith("java.") && !n.startsWith("jdk."))
			.findFirst().orElse(""));
		for (String p : UI)
			if (c.startsWith(p))
				return true;
		return false;
	}

	/** Force Planning Mode while a locked building is being created. */
	static void beginCreate(ConstructionInit init) {
		if (init == null || init.b == null || SETT.JOBS().planMode.is())
			return;
		if (locked(init.b.blue())) {
			SETT.JOBS().planMode.set(true);
			forced = true;
		}
	}

	static void endCreate() {
		if (forced) {
			forced = false;
			SETT.JOBS().planMode.set(false);
		}
	}

	/** True if this is a still-locked building under construction (must not be activated). */
	static boolean blockedRoom(Room r) {
		if (r == null || r.constructor() == null)
			return false;
		if (!r.getClass().getName().equals("settlement.room.main.construction.ConstructionInstance"))
			return false;
		return locked(r.constructor().blue());
	}

	static boolean blockedTile(int tile) {
		return blockedRoom(SETT.ROOMS().map.get(tile));
	}
}
