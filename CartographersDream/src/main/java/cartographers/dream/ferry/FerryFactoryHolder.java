package cartographers.dream.ferry;

import java.lang.reflect.Field;

import settlement.main.SETT;
import settlement.thing.halfEntity.Factory;
import settlement.thing.halfEntity.HalfEnts;
import snake2d.util.sets.ArrayList;

/**
 * Holds the FerryFactory singleton and registers it into the vanilla
 * HalfEnts constructor list via reflection.
 *
 * Previously the mod shadowed HalfEnts to insert a `public final
 * FerryFactory ferry` field and add it to the constructor list at
 * construction time. That worked in isolation but broke compatibility with
 * any other mod that also shadows HalfEnts (notably Heroes of Syx, which
 * shadows HalfEnts to add its own transports factory). When two mods
 * shadow the same class, only one classfile wins on the classpath, so
 * either ferry or HoS's transports silently disappears.
 *
 * Reflection-injecting our factory into vanilla HalfEnts's private
 * `constructors` ArrayList lets the mod coexist with any other mod that
 * touches HalfEnts: vanilla HalfEnts is loaded (everyone agrees), HoS
 * adds its factory in its own shadow if it has one, and we then append
 * the FerryFactory at the end via reflection.
 *
 * Trade-off: factory index is dependent on load order. A save written
 * with Cartographer's Dream + HoS will assign ferry an index that
 * differs from a save written without HoS. Removing or adding HoS to a
 * save that has a ferry in transit will crash the load — vanilla
 * HalfEnts looks up the factory by stored index, finds the wrong slot.
 * Workaround: don't add/remove mods mid-campaign while a ferry is
 * mid-trip. Documented in the Workshop description.
 */
public final class FerryFactoryHolder {

	private FerryFactoryHolder() {}

	private static FerryFactory instance;
	// The HalfEnts we installed the factory into. Every new game AND every
	// save load builds a FRESH HalfEnts holding only the 4 vanilla factories,
	// so caching `instance` alone (as before) meant the factory was never
	// re-registered for the new list. A save with a ferry in transit then
	// stored the ferry's factory index (4) and HalfEnts.load did
	// constructors.get(4) on a 4-element list -> ArrayIndexOutOfBounds crash
	// on load. Re-install whenever HalfEnts changes.
	private static HalfEnts installedInto;

	/**
	 * Returns the FerryFactory, installing it into the CURRENT HalfEnts on
	 * first call and re-installing whenever the settlement's HalfEnts is
	 * replaced (new game / save load). Safe to call every tick: O(1) once
	 * installed into the live HalfEnts.
	 *
	 * Returns null if installation failed (HALFENTS not ready yet, or
	 * reflection blocked). Callers must null-check and fall back to a
	 * no-op rather than NPE.
	 */
	public static FerryFactory get() {
		HalfEnts he = SETT.HALFENTS();
		if (instance != null && installedInto == he) return instance;
		instance = null;
		installedInto = null;
		install();
		return instance;
	}

	private static void install() {
		try {
			HalfEnts he = SETT.HALFENTS();
			if (he == null) return;

			Field f = HalfEnts.class.getDeclaredField("constructors");
			f.setAccessible(true);
			Object raw = f.get(he);
			if (!(raw instanceof ArrayList)) {
				cartographers.dream.CDLog.info("[CartographersDream] HalfEnts.constructors is not ArrayList (was " + (raw == null ? "null" : raw.getClass()) + "); ferry will not be registered.");
				return;
			}
			@SuppressWarnings("unchecked")
			ArrayList<Factory<?>> constructors = (ArrayList<Factory<?>>) raw;

			// Defensive: if for some reason install ran twice, do not add
			// a second FerryFactory to the constructor list.
			for (int i = 0; i < constructors.size(); i++) {
				if (constructors.get(i) instanceof FerryFactory) {
					instance = (FerryFactory) constructors.get(i);
					installedInto = he;
					cartographers.dream.CDLog.info("[CartographersDream] FerryFactory already present at index " + i + ", reusing it.");
					return;
				}
			}

			// Factory constructor self-adds to the supplied list and
			// captures its own index. Vanilla HalfEnts.update will then
			// iterate this enlarged list and tick our factory's entities
			// alongside the others without any further plumbing.
			instance = new FerryFactory(constructors);
			installedInto = he;
			cartographers.dream.CDLog.info("[CartographersDream] FerryFactory installed at index " + constructors.size());
		} catch (Throwable t) {
			cartographers.dream.CDLog.info("[CartographersDream] FerryFactoryHolder.install failed: " + t);
			cartographers.dream.CDLog.err(t);
		}
	}
}
