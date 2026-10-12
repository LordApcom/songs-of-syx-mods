package lordapcom.cdream;

import java.util.HashSet;
import java.util.Set;

import snake2d.LOG;

/** Logs each distinct failure once, so a broken hook never spams or crashes the game. */
final class Log {

	private static final Set<String> seen = new HashSet<>();

	private Log() {
	}

	static void once(String where, Throwable t) {
		if (seen.add(where))
			LOG.ln("[CartographersDream] " + where + " failed, using the vanilla behavior: " + t);
	}
}
