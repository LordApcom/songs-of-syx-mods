package settlement.room.main.construction;

import java.util.IdentityHashMap;

import planahead.TechReqsHider;
import settlement.main.SETT;

/**
 * Same-package bridge that lets the PlanAhead mod read and mutate the
 * package-private internals of {@link ConstructionInstance} and
 * {@link ConstructionBlueprint} without reflection. Placed in the game's own
 * package on purpose: the symbols below are package-default in vanilla.
 */
public final class ConstructionAccess {

    private ConstructionAccess() {}

    /**
     * Per-instance memory of the last observed `active` state for every
     * known {@link ConstructionInstance}. Identity-keyed so the entries
     * become unreachable (and overwritten by clear+putAll below) the moment
     * the game replaces the construction list — i.e. on save load or when
     * returning from a battle.
     */
    private static final IdentityHashMap<ConstructionInstance, Boolean> lastActive =
        new IdentityHashMap<>();

    /**
     * Keeps tech-locked constructions in the dormant Planning state.
     *
     * Two flip rules:
     *
     *   1. Dormant → active transition while still tech-locked → flip back.
     *      Triggered when the player toggles the global planning-mode flag
     *      off and existing dormant constructions wake up.
     *
     *   2. First sighting + active + tech-locked, ONLY while
     *      {@link TechReqsHider#isHidden()} is true (i.e. the saved-prints
     *      panel is open) AND we already have a populated baseline (so we
     *      know this isn't tick 1 after a save load). Triggered when Better
     *      Blueprints places a saved area that includes tech-locked rooms —
     *      they come out active (because planMode follows the user's
     *      setting) and the watchdog dormantises just the locked ones,
     *      leaving researched rooms in the same saved area as active so
     *      workers can start building them immediately.
     *
     * Researched constructions (whether placed in this session, loaded from
     * a save, or restored after a battle) are never flipped: rule 1 only
     * fires on a real transition, and rule 2 only fires on tech-locked
     * ones.
     */
    public static int enforceTechLock() {
        if (SETT.JOBS() == null) return 0;
        ConstructionBlueprint cb = SETT.ROOMS().construction.construction;
        IdentityHashMap<ConstructionInstance, Boolean> next =
            new IdentityHashMap<>(cb.all.size() + 8);

        boolean firstSightingFlipsAllowed =
            TechReqsHider.isHidden() && !lastActive.isEmpty();

        int flipped = 0;
        for (ConstructionInstance ci : cb.all) {
            if (ci == null) continue;
            if (ci.blueprint == null) {
                next.put(ci, ci.active);
                continue;
            }
            if (ci.blueprint.blue() == null) {
                next.put(ci, ci.active);
                continue;
            }
            boolean unlocked = TechReqsHider.isUnlocked(ci.blueprint.blue());
            Boolean prev = lastActive.get(ci);

            if (prev == null) {
                if (firstSightingFlipsAllowed && ci.active && !unlocked) {
                    ci.active = false;
                    flipped++;
                }
            } else if (Boolean.FALSE.equals(prev) && ci.active && !unlocked) {
                ci.active = false;
                flipped++;
            }
            next.put(ci, ci.active);
        }

        lastActive.clear();
        lastActive.putAll(next);
        return flipped;
    }
}
