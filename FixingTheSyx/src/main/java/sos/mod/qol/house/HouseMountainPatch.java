package sos.mod.qol.house;

import java.lang.reflect.Field;

import settlement.main.SETT;
import settlement.misc.SettPlacability;
import snake2d.util.map.MAP_BOOLEAN;

/**
 * Patches {@link SettPlacability#solidityWill} so that mountain rock no longer counts as solid
 * when the placement system asks "will this tile block the entrance?". The room construction
 * pipeline already auto-tunnels mountain tiles when {@code autoWalls} is on (see
 * {@code UtilWallPlacability.openingBuild}), but the up-front placeability check rejects house
 * placement because mountain pathing-solidity surrounds the entrance on all four sides.
 *
 * Net effect: indoor rooms (houses, taverns, etc.) become placeable inside mountain rock
 * exactly the way industries already are.
 */
public final class HouseMountainPatch {

    private HouseMountainPatch() {}

    public static void install() throws ReflectiveOperationException {
        SettPlacability placa = SETT.PLACA();
        if (placa == null) throw new IllegalStateException("SETT.PLACA() is null - install too early");

        Field f = SettPlacability.class.getDeclaredField("solidityWill");
        f.setAccessible(true);
        MAP_BOOLEAN original = (MAP_BOOLEAN) f.get(placa);
        if (original instanceof MountainTunnelableSolidity) return; // already patched

        f.set(placa, new MountainTunnelableSolidity(original));
    }

    private static final class MountainTunnelableSolidity implements MAP_BOOLEAN {
        private final MAP_BOOLEAN delegate;

        MountainTunnelableSolidity(MAP_BOOLEAN delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean is(int tile) {
            // Single-arg form is asserted unreachable in the original; keep behavior identical.
            return delegate.is(tile);
        }

        @Override
        public boolean is(int tx, int ty) {
            if (!SETT.IN_BOUNDS(tx, ty)) return delegate.is(tx, ty);
            // Mountain rock will be tunneled by the room construction job, so for placement-time
            // reachability checks treat it as non-solid.
            if (SETT.TERRAIN().MOUNTAIN.is(tx, ty)) return false;
            return delegate.is(tx, ty);
        }
    }
}
