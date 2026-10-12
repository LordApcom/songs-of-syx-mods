package planahead;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

import game.faction.FACTIONS;
import game.faction.Faction;
import init.value.Lock;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintImp;

/**
 * Snapshots and temporarily clears every {@link RoomBlueprintImp#reqs}
 * Lockable so that vanilla and modded code paths that gate on
 * {@code reqs.passes(player)} treat tech-locked rooms as if they were
 * unlocked. This is what lets Better Blueprints' saved-prints picker show,
 * preview and place tech-locked rooms instead of silently filtering or
 * skipping them.
 *
 * The clearing is scoped: the orchestrator (InstanceScript) toggles it
 * only while the prints panel is actually open, then restores the original
 * locks. Outside that window every other tech-gated game system
 * (raiders, guard tools, import stance, etc.) sees its real
 * requirements again.
 */
public final class TechReqsHider {

    @SuppressWarnings("rawtypes")
    private static final IdentityHashMap<RoomBlueprintImp, ArrayList<Lock>> snapshot
        = new IdentityHashMap<>();
    private static boolean initialized = false;
    private static boolean currentlyHidden = false;

    private TechReqsHider() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized void initialize() {
        if (initialized) return;
        if (SETT.ROOMS() == null) return;
        for (RoomBlueprintImp r : SETT.ROOMS().imps()) {
            if (r == null || r.reqs == null) continue;
            ArrayList<Lock> copy = new ArrayList<>(4);
            for (Object lock : r.reqs.all()) {
                copy.add((Lock) lock);
            }
            snapshot.put(r, copy);
        }
        initialized = true;
    }

    public static boolean isHidden() {
        return currentlyHidden;
    }

    public static void hide() {
        if (!initialized) initialize();
        if (!initialized || currentlyHidden) return;
        for (RoomBlueprintImp r : snapshot.keySet()) {
            r.reqs.clear();
        }
        currentlyHidden = true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void restore() {
        if (!currentlyHidden) return;
        for (Map.Entry<RoomBlueprintImp, ArrayList<Lock>> e : snapshot.entrySet()) {
            RoomBlueprintImp r = e.getKey();
            r.reqs.clear();
            for (Lock lock : e.getValue()) {
                r.reqs.push(lock);
            }
        }
        currentlyHidden = false;
    }

    /**
     * Snapshot-backed tech check. Works the same whether reqs are
     * currently hidden or not — always consults the original Locks
     * captured by {@link #initialize()}. Falls back to a live
     * {@code reqs.passes()} for blueprints that weren't snapshotted
     * (e.g. blueprints registered after init).
     */
    @SuppressWarnings("rawtypes")
    public static boolean isUnlocked(RoomBlueprintImp r) {
        if (r == null) return true;
        ArrayList<Lock> locks = snapshot.get(r);
        if (locks == null || locks.isEmpty()) {
            return r.reqs == null || r.reqs.passes(FACTIONS.player());
        }
        Faction p = FACTIONS.player();
        for (Lock lock : locks) {
            if (!lock.unlocker.inUnlocked(p)) return false;
        }
        return true;
    }
}
