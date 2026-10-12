package workplace.filter;

import init.type.HGROUP;
import init.type.HGROUP.HTypeBitsImp;
import init.type.HTYPE;
import init.type.HTYPES;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.main.Room;
import settlement.room.main.RoomInstance;
import settlement.stats.STATS;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.sets.LIST;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class WorkplaceFilterManager {

    /** Static instance for access from class replacement (RoomEmployments). */
    public static WorkplaceFilterManager INSTANCE;

    // Save format version markers.
    // Legacy (no marker) = single map of worker filters only.
    // VERSION_1 = two separate maps: worker + user.
    private static final int SAVE_MAGIC = 0x57535046; // "WSPF"
    private static final int SAVE_VERSION = 1;

    private final HashMap<Long, HTypeBitsImp> workerFilters = new HashMap<>();
    private final HashMap<Long, HTypeBitsImp> userFilters = new HashMap<>();
    private int tickCounter = 0;
    private int cleanupCounter = 0;
    private static final int ENFORCE_INTERVAL = 30; // safety net only — hard block in RoomEmployments handles filtering
    private static final int CLEANUP_INTERVAL = 300; // every ~5s drop orphan filter entries whose room no longer fits

    // --- Key packing ---

    private static long key(int cx, int cy) {
        return ((long) cx) << 32 | (cy & 0xFFFFFFFFL);
    }

    private static long key(RoomInstance ins) {
        // Use mX/mY (the room's "main floor tile") instead of body().cX()/cY()
        // (geometric centre of the bounding box). For irregular rooms (U-shape
        // libraries, L-shape fisheries, etc.) the geometric centre can fall on
        // a tile that's NOT part of the room body — resolving it would return a
        // different room or one without an employment system, which then NPE'd
        // when we called employees().target() (vanilla's target() chases
        // blueprint().employment().getFill() without a null guard).
        return key(ins.mX(), ins.mY());
    }

    private static int keyX(long key) {
        return (int) (key >> 32);
    }

    private static int keyY(long key) {
        return (int) key;
    }

    // --- Public API ---

    public HTypeBitsImp getWorkerFilter(RoomInstance ins) {
        if (ins == null) return null;
        return workerFilters.get(key(ins));
    }

    public HTypeBitsImp getOrCreateWorkerFilter(RoomInstance ins) {
        if (ins == null) return null;
        long k = key(ins);
        HTypeBitsImp bits = workerFilters.get(k);
        if (bits == null) {
            bits = new HTypeBitsImp(false);
            bits.setEveryone();
            workerFilters.put(k, bits);
        }
        return bits;
    }

    public void setWorkerFilter(RoomInstance ins, HTypeBitsImp bits) {
        if (ins == null) return;
        long k = key(ins);

        if (isEveryone(bits)) {
            workerFilters.remove(k);
        } else {
            HTypeBitsImp stored = workerFilters.get(k);
            if (stored == null) {
                stored = new HTypeBitsImp(false);
                workerFilters.put(k, stored);
            }
            if (stored != bits) {
                stored.copy(bits);
            }
        }

        // Immediate enforcement: fire mismatched workers
        enforceRoom(ins, workerFilters.get(k));
        // Re-plan the workforce now that the filter capacities changed (the shadowed
        // Employer caps each species by the instances that accept it).
        requestReplan();
    }

    private static void requestReplan() {
        try {
            SETT.ROOMS().employment.setTargets();
        } catch (Exception e) {
            // not in a settlement yet — the next Employer cycle picks it up anyway
        }
    }

    public void removeWorkerFilter(RoomInstance ins) {
        if (ins == null) return;
        workerFilters.remove(key(ins));
        requestReplan();
    }

    public boolean isWorkerAllowed(RoomInstance ins, Humanoid h) {
        HTypeBitsImp bits = getWorkerFilter(ins);
        if (bits == null) return true;
        HGROUP group = HGROUP.get(h);
        return group != null && bits.is(group);
    }

    public boolean hasWorkerFilter(RoomInstance ins) {
        return workerFilters.containsKey(key(ins));
    }

    public boolean hasAnyWorkerFilter() {
        return !workerFilters.isEmpty();
    }

    // --- User filter API ---

    public HTypeBitsImp getUserFilter(RoomInstance ins) {
        if (ins == null) return null;
        return userFilters.get(key(ins));
    }

    public void setUserFilter(RoomInstance ins, HTypeBitsImp bits) {
        if (ins == null) return;
        long k = key(ins);

        if (isEveryone(bits)) {
            userFilters.remove(k);
        } else {
            HTypeBitsImp stored = userFilters.get(k);
            if (stored == null) {
                stored = new HTypeBitsImp(false);
                userFilters.put(k, stored);
            }
            if (stored != bits) {
                stored.copy(bits);
            }
        }
        // No immediate enforcement for user filter — pathfinding handles it
    }

    public boolean hasUserFilter(RoomInstance ins) {
        if (ins == null) return false;
        return userFilters.containsKey(key(ins));
    }

    public boolean isUserAllowed(RoomInstance ins, Humanoid h) {
        if (ins == null || h == null) return true;

        // Bypass: hostile + special humanoids are never blocked
        HTYPE t = h.indu().hType();
        if (t == HTYPES.ENEMY() || t == HTYPES.RIOTER()
            || t == HTYPES.DERANGED() || t == HTYPES.PRISONER()) {
            return true;
        }

        HTypeBitsImp bits = getUserFilter(ins);
        if (bits == null) return true;
        HGROUP group = HGROUP.get(h);
        return group != null && bits.is(group);
    }

    // --- Enforcement ---

    public void enforce(double ds) {
        // Periodic cleanup runs even when worker filters are empty, so user-filter
        // orphans (rooms demolished or rebuilt as something else) also get pruned.
        cleanupCounter++;
        if (cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;
            cleanupOrphans();
        }

        if (workerFilters.isEmpty()) return;

        tickCounter++;
        if (tickCounter < ENFORCE_INTERVAL) return;
        tickCounter = 0;

        Iterator<Map.Entry<Long, HTypeBitsImp>> it = workerFilters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, HTypeBitsImp> entry = it.next();
            long k = entry.getKey();

            RoomInstance ins = resolveRoomFromKey(k);
            if (ins == null) {
                // Room not found — might be demolished or temporarily unresolvable.
                // Stale entries are cleaned up during save().
                continue;
            }

            if (!ins.active()) continue;

            enforceRoom(ins, entry.getValue());
        }
    }

    /**
     * MOD: Drops filter entries whose stored tile no longer hosts a room that can
     * carry the corresponding filter. Three orphan cases are pruned:
     *   1. Tile no longer covered by any room (demolished, no replacement).
     *   2. Tile now covered by a room whose blueprint has no employment system —
     *      worker filters are meaningless there. Happens when a workplace gets
     *      demolished and another (non-employment) building is placed over its
     *      old key tile, or when legacy saves stored a centre-tile that lands
     *      on a non-workplace neighbour.
     *   3. User-filter tiles whose room is no longer a service-providing building.
     *
     * Stale entries are otherwise harmless (enforceRoom has defensive guards), but
     * they accumulate in saves and slow down enforce/save loops over time. Cheap
     * to run every CLEANUP_INTERVAL ticks.
     */
    private void cleanupOrphans() {
        if (workerFilters.isEmpty() && userFilters.isEmpty()) return;

        Iterator<Map.Entry<Long, HTypeBitsImp>> wIt = workerFilters.entrySet().iterator();
        while (wIt.hasNext()) {
            Map.Entry<Long, HTypeBitsImp> entry = wIt.next();
            RoomInstance ins = resolveRoomFromKey(entry.getKey());
            if (ins == null) continue; // demolished — keep entry; save() prunes it
            if (ins.blueprintI() == null || ins.blueprintI().employment() == null) {
                // Room replaced by a non-workplace — filter can never apply here again
                wIt.remove();
            }
        }

        Iterator<Map.Entry<Long, HTypeBitsImp>> uIt = userFilters.entrySet().iterator();
        while (uIt.hasNext()) {
            Map.Entry<Long, HTypeBitsImp> entry = uIt.next();
            RoomInstance ins = resolveRoomFromKey(entry.getKey());
            if (ins == null) continue;
            // User filters apply to ROOM_SERVICE_HASER blueprints. Anything else is
            // an orphan (legacy save, room replaced, mismatched key tile).
            if (!(ins.blueprint() instanceof settlement.room.service.module.RoomService.ROOM_SERVICE_HASER)) {
                uIt.remove();
            }
        }
    }

    /**
     * MOD: Safe to fire or reassign this humanoid via STATS.WORK().EMPLOYED.set()?
     *
     * Vanilla RoomEmployment.employ() calls changeCurrent(delta, WGROUP.get(h.indu())),
     * and WGROUP.get returns null whenever the humanoid's current HTYPE has works=false
     * (Retiree, Student, Recruit, Tourist, Prisoner, etc.). A humanoid can end up in an
     * employees list with such an HTYPE if their type transitions while still employed
     * (common at food stalls and markets where vanilla doesn't always pre-fire). Touching
     * them via EMPLOYED.set would NPE inside vanilla's accounting. Skip them — vanilla
     * will clean them up when their state stabilises.
     */
    private static boolean isSafeToTouch(Humanoid h) {
        if (h == null) return false;
        try {
            if (h.indu() == null) return false;
            return init.type.WGROUP.get(h.indu()) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void enforceRoom(RoomInstance ins, HTypeBitsImp bits) {
        if (ins == null || bits == null) return;
        // Defensive: the resolved room may not even
        // have an employment system. Reading employees().employed() is safe (raw
        // count), but downstream vanilla paths chase blueprint().employment()
        // unconditionally, so just bail out for non-employment rooms.
        if (ins.blueprintI() == null) return;
        if (ins.blueprintI().employment() == null) return;
        if (ins.employees().employed() == 0) return;

        LIST<Humanoid> workers = ins.employees().employees();
        List<Humanoid> toFire = new ArrayList<>();
        for (int i = 0; i < workers.size(); i++) {
            Humanoid h = workers.get(i);
            if (!isSafeToTouch(h)) continue;
            HGROUP group = HGROUP.get(h);
            if (group != null && !bits.is(group)) {
                toFire.add(h);
            }
        }
        for (Humanoid h : toFire) {
            FilterDiag.fire(ins, h, "enforceRoom-not-allowed");
            STATS.WORK().EMPLOYED.set(h, null);
        }
    }

    private static RoomInstance resolveRoom(int tx, int ty) {
        try {
            Room room = SETT.ROOMS().map.get(tx, ty);
            if (room instanceof RoomInstance) return (RoomInstance) room;
        } catch (Exception e) {
            // Fallback silently
        }
        return null;
    }

    /**
     * Resolves a room from a stored key. Returns whatever room covers the stored
     * coordinates; for irregular rooms (e.g. fisheries with water inside the body
     * bounding box), body().cX()/cY() may not equal the room's "main" tile, so we
     * tolerate any room that happens to occupy the recorded centre tile. We do NOT
     * scan neighbours — a demolished room must NOT silently retarget the filter onto
     * a different building.
     */
    private static RoomInstance resolveRoomFromKey(long k) {
        return resolveRoom(keyX(k), keyY(k));
    }

    // --- Persistence ---

    public void save(FilePutter file) {
        file.i(SAVE_MAGIC);
        file.i(SAVE_VERSION);
        int groupCount = HGROUP.all().size();
        file.i(groupCount);

        saveFilterMap(file, workerFilters, groupCount);
        saveFilterMap(file, userFilters, groupCount);
    }

    private void saveFilterMap(FilePutter file, HashMap<Long, HTypeBitsImp> map, int groupCount) {
        // Collect only entries whose rooms still exist (clean up stale)
        List<Map.Entry<Long, HTypeBitsImp>> valid = new ArrayList<>();
        for (Map.Entry<Long, HTypeBitsImp> e : map.entrySet()) {
            if (resolveRoomFromKey(e.getKey()) != null) {
                valid.add(e);
            }
        }

        file.i(valid.size());
        for (Map.Entry<Long, HTypeBitsImp> e : valid) {
            file.l(e.getKey());
            HTypeBitsImp bits = e.getValue();
            for (int j = 0; j < groupCount; j++) {
                file.bool(bits.is(j));
            }
        }
    }

    public void load(FileGetter file) throws IOException {
        workerFilters.clear();
        userFilters.clear();
        // Trigger orphan cleanup on the first enforce tick after load so legacy
        // saves get pruned immediately rather than carrying stale state for ~5s.
        cleanupCounter = CLEANUP_INTERVAL;

        // Defensive: never let a malformed filter block crash save loading. If our block
        // is unreadable, we drop our filter state silently and let the rest of the save
        // continue. Losing filters is better than refusing to load the world.
        try {
            int first = file.i();
            if (first == SAVE_MAGIC) {
                int version = file.i();
                int groupCount = file.i();
                loadFilterMap(file, workerFilters, groupCount);
                loadFilterMap(file, userFilters, groupCount);
            } else {
                // Legacy format: first int was filter count, not magic
                int filterCount = first;
                int groupCount = file.i();
                loadFilterMapBody(file, workerFilters, filterCount, groupCount);
                // userFilters stays empty for legacy saves
            }
        } catch (Exception e) {
            workerFilters.clear();
            userFilters.clear();
            System.err.println("[WorkplaceSpeciesFilter] Load failed, cleared filter state: " + e);
        }
    }

    private void loadFilterMap(FileGetter file, HashMap<Long, HTypeBitsImp> map, int groupCount) throws IOException {
        int count = file.i();
        loadFilterMapBody(file, map, count, groupCount);
    }

    private void loadFilterMapBody(FileGetter file, HashMap<Long, HTypeBitsImp> map, int count, int groupCount) throws IOException {
        int currentGroupCount = HGROUP.all().size();
        for (int i = 0; i < count; i++) {
            long k = file.l();
            HTypeBitsImp bits = new HTypeBitsImp(false);

            int toRead = Math.min(groupCount, currentGroupCount);
            for (int j = 0; j < toRead; j++) {
                if (file.bool()) {
                    bits.set(HGROUP.all().get(j));
                }
            }
            for (int j = toRead; j < groupCount; j++) {
                file.bool(); // discard extra bits from old HGROUP sets
            }

            if (!isEveryone(bits)) {
                map.put(k, bits);
            }
        }
    }

    // --- Helpers ---

    private static boolean isEveryone(HTypeBitsImp bits) {
        if (bits == null) return true;
        for (int i = 0; i < HGROUP.all().size(); i++) {
            if (!bits.is(i)) return false;
        }
        return true;
    }
}
