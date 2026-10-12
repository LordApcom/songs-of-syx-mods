package lordapcom.bridge;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

import game.GAME;
import game.time.TIME;
import settlement.main.SETT;

/**
 * File transport + dispatcher. Everything here runs on the game's update thread
 * (called from SCRIPT_INSTANCE.update). State is static so it survives a game load.
 */
final class Bridge {

	static final Path DIR = Paths.get(System.getenv("APPDATA"), "songsofsyx", "bridge");
	private static final Path IN = DIR.resolve("in.txt");
	private static final Path OUT = DIR.resolve("out.jsonl");
	private static final Path STATUS = DIR.resolve("status.json");

	/** Marker returned by a handler that will answer later through reply(). */
	static final Object DEFERRED = new Object();

	static String currentId;
	static String pendingLoadId;
	static Run run;

	private static boolean inited;
	private static long inOff;
	private static long lastPoll, lastStatus;
	private static final ArrayDeque<Runnable> deferred = new ArrayDeque<>();

	static final class Run {
		final String id;
		final double target;
		final double cap;
		double elapsed;
		int popups;
		final int startDay;

		Run(String id, double target, double cap) {
			this.id = id;
			this.target = target;
			this.cap = cap;
			this.startDay = TIME.days().bitsSinceStart();
		}
	}

	/** Screen of the current frame: "menu", "worldgen", "landing" (screen hooks) or "settlement" (script update). */
	static String screen = "menu";
	private static long lastHook;

	/** Called by the screen mixins every frame of a screen without a settlement. */
	static void tickScreen(String s, double ds) {
		screen = s;
		lastHook = System.nanoTime();
		tick0(ds);
	}

	private static long lastAny;

	/** VIEW.update fallback: only ticks when nothing else did for 300 ms (a popup is holding the game). */
	static void viewTick() {
		if (System.nanoTime() - lastAny < 300_000_000L)
			return;
		try {
			// SETT exists already during world generation; only the settlement view itself counts
			if (settlement.main.SETT.exists() && view.main.VIEW.s().isActive())
				screen = "settlement";
		} catch (Throwable t) {
			// not in a game
		}
		tick0(0);
	}

	/** Called by SCRIPT_INSTANCE.update; the script also ticks under worldgen/landing, where a hook already ran. */
	static void tick(double ds) {
		if (System.nanoTime() - lastHook > 300_000_000L && view.main.VIEW.s().isActive())
			screen = "settlement";
		tick0(ds);
	}

	/** Off unless the player created the file ENABLED in the bridge folder (rechecked every 5 s, no restart needed). */
	static final Path ENABLED = DIR.resolve("ENABLED");
	private static boolean enabled;
	private static long lastEnableCheck = Long.MIN_VALUE;

	private static void tick0(double ds) {
		lastAny = System.nanoTime();
		if (lastAny - lastEnableCheck > 5_000_000_000L || lastEnableCheck == Long.MIN_VALUE) {
			lastEnableCheck = lastAny;
			boolean was = enabled;
			enabled = Files.exists(ENABLED);
			if (enabled && !was)
				inited = false; // commands written while switched off are dropped, not replayed
		}
		if (!enabled)
			return; // a subscriber who never opted in gets no files, no commands and no cheats
		try {
			if (!inited)
				init();
			if (pendingLoadId != null) {
				String id = pendingLoadId;
				pendingLoadId = null;
				reply(id, true, Commands.status(), null);
			}
			if (run != null) {
				// a player would close notices while letting time run; they pause the game otherwise
				if (view.main.VIEW.messages().activated()) {
					view.main.VIEW.messages().hideAll();
					run.popups++;
				}
				advanceRun(ds);
			}
			long now = System.nanoTime();
			if (now - lastPoll >= 200_000_000L) {
				lastPoll = now;
				poll();
			}
			if (now - lastStatus >= 1_000_000_000L) {
				lastStatus = now;
				writeStatus();
			}
		} catch (Throwable t) {
			// never crash the game from the bridge
			System.err.println("[SosTestBridge] tick failed: " + t);
		}
	}

	static void afterUpdate() {
		Runnable r;
		while ((r = deferred.poll()) != null) {
			try {
				r.run();
			} catch (Throwable t) {
				System.err.println("[SosTestBridge] deferred failed: " + t);
			}
		}
	}

	static void defer(Runnable r) {
		deferred.add(r);
	}

	private static void init() throws Exception {
		Files.createDirectories(DIR);
		// stale backlog from a previous game process must not replay
		Files.write(IN, new byte[0]);
		Files.write(OUT, new byte[0]);
		inOff = 0;
		inited = true;
	}

	private static void advanceRun(double ds) {
		Run r = run;
		r.elapsed += ds;
		double remaining = r.target - r.elapsed;
		if (remaining <= 0) {
			run = null;
			GAME.SPEED.speedSet(0);
			Map<String, Object> m = Commands.status();
			m.put("elapsedGameSeconds", r.elapsed);
			m.put("popupsClosed", r.popups);
			m.put("daysPassed", TIME.days().bitsSinceStart() - r.startDay);
			reply(r.id, true, m, null);
			return;
		}
		// fixed speed for the whole run; the last frame may overshoot a little, the reply reports the real elapsed time
		if (GAME.SPEED.speedTarget() != r.cap)
			GAME.SPEED.speedSet(r.cap);
	}

	private static void poll() throws Exception {
		long len = Files.exists(IN) ? Files.size(IN) : 0;
		if (len < inOff)
			inOff = 0;
		if (len == inOff)
			return;
		byte[] buf = new byte[(int) (len - inOff)];
		try (RandomAccessFile f = new RandomAccessFile(IN.toFile(), "r")) {
			f.seek(inOff);
			f.readFully(buf);
		}
		int end = buf.length;
		while (end > 0 && buf[end - 1] != '\n')
			end--;
		if (end == 0)
			return; // partial line, wait for the rest
		inOff += end;
		String text = new String(buf, 0, end, StandardCharsets.UTF_8);
		for (String line : text.split("\n")) {
			line = line.trim();
			if (!line.isEmpty() && line.charAt(0) != '#')
				dispatch(line);
		}
	}

	private static void dispatch(String line) {
		int sp = line.indexOf(' ');
		String id = sp < 0 ? line : line.substring(0, sp);
		String cmd = sp < 0 ? "" : line.substring(sp + 1).trim();
		currentId = id;
		try {
			String[] args = cmd.split("[ \t]+");
			String c = args[0].toLowerCase();
			Object res = null;
			if (c.equals("screen") || (c.equals("status") && !screen.equals("settlement")))
				res = Commands.m("screen", screen);
			else if (!screen.equals("settlement") || c.equals("quit") || c.equals("newgame") || c.equals("continue"))
				res = Flow.exec(screen, args);
			if (res == null) {
				if (!screen.equals("settlement") && !c.equals("ping") && !c.equals("eval"))
					throw new IllegalStateException("'" + c + "' needs a settlement; screen is " + screen);
				res = Commands.exec(args);
			}
			if (res != DEFERRED)
				reply(id, true, res, null);
		} catch (Throwable t) {
			Throwable c = t;
			while (c.getCause() != null && (c instanceof java.lang.reflect.InvocationTargetException))
				c = c.getCause();
			reply(id, false, null, c.toString());
		}
	}

	static void reply(String id, boolean ok, Object result, String error) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", id);
		m.put("ok", ok);
		if (ok)
			m.put("result", result);
		else
			m.put("error", error);
		try {
			Files.write(OUT, (Json.write(m) + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		} catch (Throwable t) {
			System.err.println("[SosTestBridge] reply failed: " + t);
		}
	}

	private static void writeStatus() throws Exception {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("alive", true);
		m.put("epochMs", System.currentTimeMillis());
		m.put("screen", screen);
		if (screen.equals("settlement"))
			m.putAll(Commands.status());
		Files.write(STATUS, Json.write(m).getBytes(StandardCharsets.UTF_8));
	}
}
