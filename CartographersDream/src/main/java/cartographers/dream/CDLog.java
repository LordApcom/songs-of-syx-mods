package cartographers.dream;

/**
 * Mod logging that never masquerades as a crash.
 *
 * The engine (snake2d.Errors) replaces System.err with a buffer and treats
 * ANY non-empty stderr content as a crash "dump", surfacing it to the player
 * as a popup. The mod used to log routine diagnostics (ferry lifecycle,
 * path-connector anchor checks, etc.) straight to System.err, so normal
 * operation spammed the player with dozens of fake crash popups.
 *
 * LOG.ln writes to System.out, which the engine buffers separately and does
 * NOT treat as a crash dump — so nothing here can pop up.
 *
 * - {@link #info} is routine noise, silent unless debug is on.
 * - {@link #err} is a handled error worth keeping in the game log, but it is
 *   still NOT a crash, so it must not reach the stderr popup path either.
 */
public final class CDLog {

	private CDLog() {}

	/** Flip on with -Dcartographers.dream.debug=true to surface routine diagnostics. */
	public static boolean DEBUG = Boolean.getBoolean("cartographers.dream.debug");

	/** Routine diagnostics — silent by default so they never reach the crash-dump popup. */
	public static void info(Object msg) {
		if (DEBUG) snake2d.LOG.ln(msg);
	}

	/** Handled errors — recorded in the game log (System.out), never the crash popup. */
	public static void err(Object msg) {
		snake2d.LOG.ln(msg);
	}
}
