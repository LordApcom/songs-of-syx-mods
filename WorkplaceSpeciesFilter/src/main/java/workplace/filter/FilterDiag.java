package workplace.filter;

import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomInstance;
import snake2d.LOG;

/**
 * Lightweight diagnostic logging for filter hire/fire decisions. Off by default.
 *
 * Enable by adding {@code -Dworkplace.filter.diag=true} to the game JVM args.
 * When enabled, every hire/fire decision involving a filtered room writes a
 * single line to the game log, prefixed with [WSPF-DIAG], so users hitting the
 * hire/fire flip can grab their log and the failing decision shows up
 * immediately (which check fired, which room, which humanoid, what state).
 *
 * Calls are inlined and skip the LOG path when disabled, so the in-production
 * overhead is one boolean read per call site — safe to leave sprinkled.
 */
public final class FilterDiag {

    private static final boolean ENABLED =
        Boolean.getBoolean("workplace.filter.diag");

    private FilterDiag() {}

    public static boolean enabled() {
        return ENABLED;
    }

    public static void fire(RoomInstance ins, Humanoid h, String reason) {
        if (!ENABLED) return;
        log("FIRE", ins, h, reason);
    }

    public static void keep(RoomInstance ins, Humanoid h, String reason) {
        if (!ENABLED) return;
        log("KEEP", ins, h, reason);
    }

    public static void hire(RoomInstance ins, Humanoid h, String reason) {
        if (!ENABLED) return;
        log("HIRE", ins, h, reason);
    }

    private static void log(String tag, RoomInstance ins, Humanoid h, String reason) {
        try {
            StringBuilder sb = new StringBuilder(96);
            sb.append("[WSPF-DIAG] ").append(tag).append(" reason=").append(reason);
            if (ins != null) {
                sb.append(" room=");
                try {
                    sb.append(ins.blueprintI().info.name);
                } catch (Throwable t) {
                    sb.append("?");
                }
                sb.append('@').append(ins.mX()).append(',').append(ins.mY());
                try {
                    sb.append(" emp=").append(ins.employees().employed())
                      .append('/').append(ins.employees().target());
                } catch (Throwable t) {
                    sb.append(" emp=?");
                }
            }
            if (h != null) {
                sb.append(" h=");
                try {
                    sb.append(h.race().info.name);
                } catch (Throwable t) {
                    sb.append("?");
                }
                sb.append('/');
                try {
                    sb.append(h.indu().hType().key);
                } catch (Throwable t) {
                    sb.append("?");
                }
            }
            LOG.ln(sb.toString());
        } catch (Throwable ignored) {
            // Diagnostic must never crash the game
        }
    }
}
