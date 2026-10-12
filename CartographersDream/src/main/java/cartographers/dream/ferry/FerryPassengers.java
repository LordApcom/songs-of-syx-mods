package cartographers.dream.ferry;

import java.io.IOException;

import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;

/**
 * Tracks humanoids that were freshly spawned at a ferry-pickup tile and
 * are waiting for their boat to arrive. The waiting phase is intentionally
 * brief (about EMBARK_DELAY_SECONDS) so the player visually registers the
 * spawn happening on border land before the boat picks them up. After the
 * delay we hide the humanoid (moveIntoTheTheUnknown) and create a Ferry
 * HalfEntity carrying them across the cached water path.
 *
 * Tick-driven from MainScript.update via FerryPassengers.tick(ds).
 *
 * Save/load: written/read by MainScript via the static save/load methods.
 */
public final class FerryPassengers {

	private FerryPassengers() {}

	private static final double EMBARK_DELAY_SECONDS = 2.0;
	private static final int CAP = 256;

	private static final int[] hids = new int[CAP];
	private static final double[] timers = new double[CAP];
	private static int size = 0;

	public static void clear() {
		size = 0;
	}

	public static void register(Humanoid h) {
		if (size >= CAP) return;
		hids[size] = h.id();
		timers[size] = EMBARK_DELAY_SECONDS;
		size++;
	}

	public static void tick(double ds) {
		if (!FerryScenario.isActive()) return;
		int[] pathX = FerryScenario.pathX();
		int[] pathY = FerryScenario.pathY();
		if (pathX == null || pathX.length == 0) return;

		int dropTx = FerryScenario.dropTx();
		int dropTy = FerryScenario.dropTy();

		// Iterate; remove entries by swap-with-last when consumed.
		int i = 0;
		while (i < size) {
			timers[i] -= ds;
			Humanoid h = humanoidById(hids[i]);
			if (h == null) {
				removeAt(i);
				continue;
			}
			if (timers[i] <= 0) {
				// Time to embark.
				if (embark(h, pathX, pathY, dropTx, dropTy)) {
					removeAt(i);
					continue;
				} else {
					// Embark failed — could not create boat. Drop the
					// passenger from the queue; humanoid stays where it
					// is (visible on border) and will idle. Avoid retry
					// loop.
					removeAt(i);
					continue;
				}
			}
			i++;
		}
	}

	private static boolean embark(Humanoid h, int[] pathX, int[] pathY, int dropTx, int dropTy) {
		FerryFactory ff = FerryFactoryHolder.get();
		if (ff == null) return false;
		SETT.ENTITIES().moveIntoTheTheUnknown(h);
		boolean ok = ff.make(h, pathX, pathY, dropTx, dropTy);
		if (!ok) {
			// Restore humanoid to grid if boat could not be created.
			SETT.ENTITIES().returnFromTheTheUnknown(h);
		}
		return ok;
	}

	private static void removeAt(int i) {
		size--;
		hids[i] = hids[size];
		timers[i] = timers[size];
	}

	private static Humanoid humanoidById(int id) {
		ENTITY e = SETT.ENTITIES().getByID(id);
		return (e instanceof Humanoid) ? (Humanoid) e : null;
	}

	public static void save(FilePutter file) {
		file.i(size);
		for (int i = 0; i < size; i++) {
			file.i(hids[i]);
			file.d(timers[i]);
		}
	}

	public static void load(FileGetter file) throws IOException {
		// The save format is: i(size) followed by size (i, d) pairs. If we
		// silently cap size to CAP without consuming the excess bytes, the
		// FileGetter stream stays mis-aligned and every subsequent block in
		// the save file is read at the wrong offset — corrupting the entire
		// save. Read the on-disk size first, drain any overflow, then store
		// the clamped count. Caught by round-2 audit.
		int onDiskSize = file.i();
		// Negative size means the save block is corrupted (or this load is
		// being called against a non-FerryPassengers block by mistake).
		// We can't reliably drain N bytes when N is undefined, so clear
		// state, swallow the read, and let the caller's outer try/catch
		// handle the broken file. Caught by round-4 audit.
		if (onDiskSize < 0) {
			cartographers.dream.CDLog.info("[CartographersDream] FerryPassengers.load: corrupted block (size=" + onDiskSize + "), clearing");
			size = 0;
			return;
		}
		int keep = Math.min(onDiskSize, CAP);
		for (int i = 0; i < keep; i++) {
			hids[i] = file.i();
			timers[i] = file.d();
		}
		for (int i = keep; i < onDiskSize; i++) {
			file.i();
			file.d();
		}
		size = keep;
	}
}
