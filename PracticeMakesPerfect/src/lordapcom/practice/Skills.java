package lordapcom.practice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import game.battle.div.Div;
import game.boosting.BSourceInfo;
import game.boosting.BValue;
import game.boosting.BoostSpec;
import game.boosting.Boostable;
import game.boosting.BoosterValue;
import game.faction.FACTIONS;
import game.faction.npc.FactionNPC;
import game.faction.player.Player;
import game.time.TIME;
import init.sprite.UI.UI;
import init.tech.TECH;
import init.tech.TECHS;
import init.type.HCLASS_RACE;
import settlement.main.SETT;
import settlement.room.main.employment.RoomEmployment;
import settlement.stats.Induvidual;
import snake2d.LOG;
import world.map.regions.Region;

/**
 * Experience per production boostable (ROOM_FARM_GRAIN, ROOM_MINE_ORE...). The production-only boosts of the
 * production tech trees are taken off the techs and replaced by one experience booster per boostable, worth up to
 * what all those tech levels gave together. Experience grows while the city works that kind of room, grows at a third
 * of the speed from the other rooms of the same tree, and fades when the whole tree stands still. Researched levels
 * of the old techs speed up the growth.
 */
final class Skills {

	/** Tech trees whose room boosts become experience. Unlocks and tool boosts in them are left alone. */
	private static final String[] TREES = { "AGRI", "HUSB", "MINES", "REFINER", "WORKSHOP" };
	/**
	 * Pace in game years. Each day a busy type closes 1/(YEARS * days per year) of the gap to full experience, so it
	 * learns fastest at the start and slower near the top: 1 - e^(-years/YEARS), about 22% after 5 years, 39% after
	 * 10, 63% after 20 with no research.
	 */
	static final double YEARS = 20;
	static final double SIBLING = 1.0 / 3;
	/** Idle fields lose this share of the same pace, applied to what they hold. */
	static final double DECAY = 0.5;
	/** All old tech levels researched = this many times faster. */
	static final double RESEARCH_SPEED = 3;

	static final class Skill {
		final Boostable bo;
		final String tree;
		final ArrayList<TECH> techs = new ArrayList<>();
		double cap;
		int levels;
		double m;
		RoomEmployment emp;

		Skill(Boostable bo, String tree) {
			this.bo = bo;
			this.tree = tree;
		}
	}

	static final Map<String, Skill> ALL = new LinkedHashMap<>();
	private static int errs, setups;

	/** For tests: set one type's experience (0..1). */
	static String set(String key, double m) {
		ALL.get(key).m = Math.max(0, Math.min(1, m));
		return key + " m=" + ALL.get(key).m;
	}

	/** For tests: amount of a resource the city produced over the last whole days. */
	static int produced(String res, int days) {
		init.resources.RESOURCE r = null;
		for (init.resources.RESOURCE x : Lists.of(init.resources.RESOURCES.ALL()))
			if (x.key.equals(res))
				r = x;
		util.statistics.HISTORY_INT h = FACTIONS.player().res().in(game.faction.FResources.RTYPE.PRODUCED).history(r.tr());
		int sum = 0;
		for (int i = 1; i <= days; i++)
			sum += h.get(i);
		return sum;
	}

	/** For tests: a tech's description as the tech screen shows it. */
	static String techDesc(String key) {
		for (TECH t : Lists.of(TECHS.ALL()))
			if (t.key.equals(key))
				return t.name() + ": " + t.desc();
		return null;
	}

	/** For tests: one boostable's value for the first NPC faction. */
	static double npcNow(String key) {
		return ALL.get(key).bo.get(Lists.of(FACTIONS.NPCs()).get(0));
	}

	/** For tests and bug reports: one line per production type. */
	static String report() {
		StringBuilder b = new StringBuilder("setups=" + setups + " errs=" + errs + " pace=" + pace() + " npc=" + npc() + " trees=");
		for (init.tech.TechTree t : Lists.of(TECHS.TREES()))
			b.append(t.key).append(',');
		for (Skill k : ALL.values())
			b.append(String.format("\n%s %s m=%.4f cap=%.2f research=%.2f busy=%s room=%s now=%.3f", k.bo.key, k.tree, k.m, k.cap,
					researched(k), busy(k), k.emp == null ? "-" : k.emp.blueprint().key(), k.bo.get(FACTIONS.player())));
		return b.toString();
	}

	private Skills() {
	}

	static void err(String where, Throwable t) {
		if (errs++ < 5)
			LOG.err("[PracticeMakesPerfect] " + where + ": " + t);
	}

	private static boolean ours(String tree) {
		for (String t : TREES)
			if (t.equals(tree))
				return true;
		return false;
	}

	/** Called once per game setup, before the game builds the tech boosts. */
	static void setup() {
		setups++;
		ALL.clear();
		for (TECH t : Lists.of(TECHS.ALL())) {
			if (!ours(t.tree.key))
				continue;
			ArrayList<BoostSpec> specs = new ArrayList<>();
			for (BoostSpec s : Lists.of(t.boosters.all()))
				// ROOM_CONSUMPTION_* (input use per output) stays a tech: only output gets experience
				if (!s.booster.isMul && s.booster.to() > 0 && s.boostable.key.startsWith("ROOM_")
						&& !s.boostable.key.startsWith("ROOM_CONSUMPTION_"))
					specs.add(s);
			for (BoostSpec s : specs) {
				Skill k = ALL.computeIfAbsent(s.boostable.key, x -> new Skill(s.boostable, t.tree.key));
				k.cap += s.booster.to() * t.levelMax;
				if (!k.techs.contains(t)) {
					k.techs.add(t);
					k.levels += t.levelMax;
				}
				t.boosters.remove(s);
			}
		}
		try {
			describeTechs();
		} catch (Throwable t) {
			err("tech text", t);
		}
		BSourceInfo info = new BSourceInfo("Experience",
				"Grows while your city works this kind of room, a third as fast from related rooms, and fades when the whole field stands still. Research in this field speeds it up.",
				null, UI.icons().s.vial);
		for (Skill k : ALL.values())
			new BoosterValue(new Value(k), info, 0, k.cap, false).add(k.bo);
		LOG.ln("[PracticeMakesPerfect] experience on " + ALL.size() + " production types");
	}

	static void clear() {
		for (Skill k : ALL.values()) {
			k.m = 0;
			k.emp = null;
		}
	}

	/** Share of the remaining gap a fully busy type closes per day. */
	private static double pace() {
		// a cycle's length is one turn of its parent: hours -> one day, seasons -> one year
		return TIME.hours().cycleSeconds() / (YEARS * TIME.seasons().cycleSeconds());
	}

	/** NPC factions have no rooms to watch: they learn every field as if always busy, without research, since day one. */
	static double npc() {
		return 1 - Math.exp(-pace() * TIME.days().bitsSinceStart());
	}

	/** Tech screen: the old production techs now say what they do. */
	private static void describeTechs() throws ReflectiveOperationException {
		java.lang.reflect.Field f = TECH.class.getDeclaredField("info");
		f.setAccessible(true);
		Map<TECH, StringBuilder> names = new LinkedHashMap<>();
		for (Skill k : ALL.values())
			for (TECH t : k.techs) {
				StringBuilder b = names.computeIfAbsent(t, x -> new StringBuilder());
				b.append(b.length() == 0 ? "" : ", ").append(k.bo.name);
			}
		for (Map.Entry<TECH, StringBuilder> e : names.entrySet()) {
			TECH t = e.getKey();
			CharSequence old = t.desc();
			String add = "Practice Makes Perfect: this research no longer raises production by itself. Each level speeds up how fast your city gains experience in "
					+ e.getValue() + ". With every level of every related tech researched, experience grows up to "
					+ (int) RESEARCH_SPEED + " times as fast.";
			f.set(t, new util.info.INFO(t.name(), old == null || old.length() == 0 ? add : old + "\n\n" + add));
		}
	}

	/** One game day. */
	static void day() {
		bindRooms();
		double step = pace();
		Map<String, Boolean> treeBusy = new LinkedHashMap<>();
		for (Skill k : ALL.values())
			if (busy(k))
				treeBusy.put(k.tree, true);
		for (Skill k : ALL.values()) {
			double speed = step * (1 + (RESEARCH_SPEED - 1) * researched(k));
			if (busy(k))
				k.m += speed * (1 - k.m);
			else if (treeBusy.containsKey(k.tree))
				k.m += speed * SIBLING * (1 - k.m);
			else
				k.m -= step * DECAY * k.m;
			k.m = Math.max(0, Math.min(1, k.m));
		}
	}

	private static boolean busy(Skill k) {
		return k.emp != null && k.emp.employed() > 0;
	}

	/** Share of the old tech levels for this boostable that the player has researched, 0..1. */
	static double researched(Skill k) {
		if (k.levels == 0)
			return 0;
		int l = 0;
		for (TECH t : k.techs)
			l += FACTIONS.player().tech.level(t);
		return (double) l / k.levels;
	}

	/** ROOM_FARM_GRAIN is worked in the room type FARM_GRAIN. Types without a room only gain from related rooms. */
	private static void bindRooms() {
		for (Skill k : ALL.values()) {
			if (k.emp != null)
				continue;
			String key = k.bo.key.substring("ROOM_".length());
			for (RoomEmployment e : Lists.of(SETT.ROOMS().employment.ALL()))
				if (e.blueprint().key().equals(key))
					k.emp = e;
		}
	}

	/** Experience of the player's city, or the NPC curve. */
	private static final class Value implements BValue {
		private final Skill k;

		Value(Skill k) {
			this.k = k;
		}

		private double of(Object faction) {
			return faction == FACTIONS.player() ? k.m : npc();
		}

		@Override
		public double vGet(Region reg) {
			return of(reg.faction());
		}

		@Override
		public double vGet(Induvidual indu) {
			return of(indu.faction());
		}

		@Override
		public double vGet(Div div) {
			return 0;
		}

		@Override
		public double vGet(HCLASS_RACE reg) {
			return k.m;
		}

		@Override
		public double vGet(Player f) {
			return k.m;
		}

		@Override
		public double vGet(FactionNPC f) {
			return npc();
		}
	}
}
