package workplace.filter;

import settlement.entity.humanoid.Humanoid;

/**
 * Thread-local holder for the humanoid currently performing a service pathfind.
 * Set by class-replaced AISUB_walkTo before calling finder.reserve(),
 * read by class-replaced SFinderRoomService.getReservable() to check user filter.
 *
 * The try/finally pattern at call sites guarantees clear() runs even on exception,
 * so there's no risk of stale context leaking to unrelated pathfinds.
 */
public final class UserFilterContext {

    private static final ThreadLocal<Humanoid> CURRENT = new ThreadLocal<>();

    private UserFilterContext() {}

    public static void set(Humanoid h) {
        CURRENT.set(h);
    }

    public static Humanoid get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
