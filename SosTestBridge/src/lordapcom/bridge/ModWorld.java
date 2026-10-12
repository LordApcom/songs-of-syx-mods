package lordapcom.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import game.GAME;
import game.event.engine.Event;
import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.DipStance;
import game.faction.diplomacy.deal.Deal;
import game.faction.diplomacy.deal.DealBool;
import game.faction.diplomacy.deal.DealParty;
import game.faction.npc.FactionNPC;
import game.faction.royalty.opinion.ROPINION;
import game.raiding.Raider;
import init.race.RACES;
import init.trade.TR;
import init.trade.TRADABLE;
import init.trade.TRADE_TYPE;
import settlement.main.SETT;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.rnd.RND;
import world.WORLD;
import world.army.AD;
import world.army.WDivRegional;
import world.entity.army.WArmy;
import world.map.pathing.WRegFinder.RegDist;
import world.map.pathing.WRegFinder.Treaty;
import world.map.pathing.WRegSel;
import world.map.regions.Region;
import world.region.RD;

/** World map, factions, diplomacy, raids, armies. Reuses the game's own debug panels and the deal (diplomacy) screen logic. */
final class ModWorld {

	static void register() {
		Registry.add("faction", "faction [<idx|name>] - list active kingdoms (stance, opinion, power, reachability) or detail one", a -> faction(a));
		Registry.add("dip", "dip | dip <faction> <war|peace|trade|pact|ally|vassal|overlord|cancel> [force] | dip <faction> <stance> <faction2> - change stance (vassal: <faction> becomes the vassal of the player, or of faction2); no args lists stances", a -> dip(a));
		Registry.add("gift", "gift | gift <faction> <credits|resourceKey> <n> - give n to a faction (n<0: faction gives to you); no args lists keys", a -> gift(a));
		Registry.add("region", "region [list [<faction|none>]] | region <idx|name> | region claim <idx|name> [<faction|none>] - world regions and ownership", a -> region(a));
		Registry.add("wpath", "wpath | wpath <regionA> <regionB> - no args: player's neighbours + trade partners; two regions: world path distance + treaty reachability", a -> wpath(a));
		Registry.add("raid", "raid | raid demand | raid now [powerMul] | raid clear - raider status, demand-then-attack flow, instant invasion of the capital, cancel", a -> raid(a));
		Registry.add("army", "army list [<faction>] | army spawn <faction> [divs] [region] | army clear <faction> - world armies", a -> army(a));
		Registry.add("wship", "wship [<faction>] - shipments travelling on the world map (gifts, taxes, trade, spoils); with a faction, only those going to it", a -> {
			Faction only = a.length > 1 ? fac(a[1]) : null;
			List<Object> l = new ArrayList<>();
			for (world.entity.WEntity e : Lists.of(WORLD.ENTITIES().fillTiles(0, WORLD.TWIDTH(), 0, WORLD.THEIGHT())))
				if (e instanceof world.entity.caravan.Shipment s && (only == null || s.faction() == only)) {
					List<String> load = new ArrayList<>();
					for (TRADABLE t : Lists.of(TR.ALL()))
						if (s.loadGet(t) > 0)
							load.add(t.key() + "=" + s.loadGet(t));
					l.add(Commands.m("x", s.ctx(), "y", s.cty(), "type", s.type().index, "to", s.destination() == null ? null : s.destination().info.name().toString(), "load", load));
				}
			return l;
		});
		Registry.add("vassal", "vassal [list] | vassal tribute [accept|decline] - who is whose vassal; the player's yearly tribute from its vassals, accepted or declined like the message's buttons", a -> vassal(a));
		Registry.add("caravan", "caravan <fromRegion> <toRegion> [<key> <n>]... [trade|tax|spoils|diplomacy] - send a shipment on the world map (keys: gift with no args)", a -> caravan(a));
		Registry.add("gevent", "gevent | gevent <key> | gevent expire - game event engine (disasters, riots, uprisings...): list, fire, end current", a -> gevent(a));
	}

	// ---- lookups ------------------------------------------------------------------------------

	private static Faction fac(String s) {
		if (s.equalsIgnoreCase("player"))
			return FACTIONS.player();
		Faction f = null;
		try {
			int i = Integer.parseInt(s);
			if (i >= 0 && i < FACTIONS.MAX())
				f = FACTIONS.getByIndex(i);
		} catch (NumberFormatException e) {
			for (Faction x : Lists.of(FACTIONS.active()))
				if (("" + x.name).equalsIgnoreCase(s) || (f == null && ("" + x.name).toLowerCase().contains(s.toLowerCase())))
					f = x;
		}
		if (f == null || !f.isActive())
			throw new IllegalArgumentException("no active faction '" + s + "' (see: faction)");
		return f;
	}

	private static FactionNPC npc(String s) {
		Faction f = fac(s);
		if (!(f instanceof FactionNPC))
			throw new IllegalArgumentException("need a kingdom, not the player");
		return (FactionNPC) f;
	}

	private static Region reg(String s) {
		try {
			int i = Integer.parseInt(s);
			if (i >= 0 && i < WORLD.REGIONS().all().size())
				return WORLD.REGIONS().getByIndex(i);
		} catch (NumberFormatException e) {
			for (Region r : Lists.of(WORLD.REGIONS().all()))
				if (r.active() && ("" + r.info.name()).equalsIgnoreCase(s))
					return r;
		}
		throw new IllegalArgumentException("no region '" + s + "' (see: region list)");
	}

	private static Map<String, Object> reg1(Region r) {
		Faction f = r.faction();
		return Commands.m("idx", r.index(), "name", "" + r.info.name(), "owner", f == null ? "none" : "" + f.name, "ownerIdx", f == null ? -1 : f.index(),
				"capitol", r.capitol(), "x", r.cx(), "y", r.cy());
	}

	// ---- factions -----------------------------------------------------------------------------

	// game path: view/world/ui/faction (kingdom panel) reads these: game/faction/diplomacy/DIP.java:228 get, game/faction/royalty/opinion/ROPINION.java:74 get
	private static Object faction(String[] a) {
		if (a.length > 1) {
			FactionNPC f = npc(a[1]);
			List<Object> regs = new ArrayList<>();
			for (Region r : Lists.of(f.realm().all()))
				regs.add(reg1(r));
			Map<String, Object> m = row(f);
			m.put("regionList", regs);
			m.put("capitolIdx", f.capitolRegion().index());
			return m;
		}
		List<Object> l = new ArrayList<>();
		for (FactionNPC f : Lists.of(FACTIONS.NPCs()))
			l.add(row(f));
		return l;
	}

	private static Map<String, Object> row(FactionNPC f) {
		return Commands.m("idx", f.index(), "name", "" + f.name, "race", f.race().key, "regions", f.realm().regions(), "stance", DIP.get(f).key(),
				"opinion", ROPINION.get(f), "power", AD.power().get(f), "credits", (long) f.credits().credits(), "armies", f.armies().all().size(),
				"reachable", RD.DIST().reachable(f), "bordersPlayer", RD.DIST().factionHasRegionBorderingPlayer(f));
	}

	// ---- diplomacy ----------------------------------------------------------------------------

	private static final String[] STANCES = { "war", "peace", "trade", "pact", "ally", "vassal", "overlord", "cancel" };

	// game path: game/faction/diplomacy/deal/DealBools.java:78..:215 (the toggles of the diplomacy screen) -> ROPINION.STANCE().setNewStance (OpsStance.java:288);
	//   force / NPC-vs-NPC: view/world/ui/faction/Debug.java:30,43,55 DIP.WAR()/NEUTRAL().set
	private static Object dip(String[] a) {
		if (a.length < 3) {
			List<Object> l = new ArrayList<>();
			for (FactionNPC f : Lists.of(FACTIONS.NPCs()))
				l.add(Commands.m("idx", f.index(), "name", "" + f.name, "stance", DIP.get(f).key()));
			return Commands.m("stances", STANCES, "vsPlayer", l);
		}
		FactionNPC f = npc(a[1]);
		String s = a[2].toLowerCase();
		boolean force = a.length > 3 && a[3].equalsIgnoreCase("force");
		if (a.length > 3 && !force) { // two kingdoms, as the world debug panel does
			Faction g = fac(a[3]);
			if (s.equals("vassal") || s.equals("overlord"))
				stance(s).set(g, f); // so "<f> vassal <g>" reads as: f becomes g's vassal ("overlord": g becomes f's)
			else
				stance(s).set(f, g);
			return Commands.m("a", "" + f.name, "b", "" + g.name, "stance", DIP.get(f, g).key());
		}
		Deal d = new Deal();
		d.setFactionAndClear(f);
		DealBool b;
		switch (s) {
		case "war": b = d.bools.WAR; break;
		case "peace": b = d.bools.PEACE; break;
		case "trade": b = d.bools.TRADE; break;
		case "pact": b = d.bools.PACT; break;
		case "ally": b = d.bools.ALLY; break;
		case "vassal": b = d.bools.VASSAL; break; // DealBools.java:196 "Protector": they become our vassal
		case "overlord": b = d.bools.OVERLORD; break; // DealBools.java:198 "Become Vassal": we become theirs
		case "cancel": b = d.bools.CANCEL_ALL; break;
		default: throw new IllegalArgumentException("stance: " + String.join("|", STANCES));
		}
		b.set(true);
		CharSequence problem = b.problem();
		if (problem != null && !force)
			throw new IllegalArgumentException("game rule blocks it: " + problem + " (add 'force' to use the debug path)");
		if (problem != null)
			stance(s).set(f);
		else
			d.execute(false); // the screen also needs the credit balance >= 0 and sends opinionChange; skipped here: the deal is free
		return Commands.m("faction", "" + f.name, "stance", DIP.get(f).key(), "forced", problem != null, "opinion", ROPINION.get(f));
	}

	private static DipStance stance(String s) {
		switch (s) {
		case "war": return DIP.WAR();
		case "peace": case "cancel": return DIP.NEUTRAL();
		case "trade": return DIP.TRADE();
		case "pact": return DIP.PACT();
		case "ally": return DIP.ALLY();
		// DIP.java:261 set(instigator, accepter, dip): OVERLORD makes the accepter the vassal, VASSAL the instigator
		case "vassal": return DIP.OVERLORD();
		case "overlord": return DIP.VASSAL();
		default: throw new IllegalArgumentException("stance: " + String.join("|", STANCES));
		}
	}

	// game path: game/faction/diplomacy/deal/Deal.java:44 execute + DealParty.java:93 execute (credits / caravan of resources), gift opinion via ROPINION.GIFTS()
	private static Object gift(String[] a) {
		if (a.length < 4) {
			List<String> keys = new ArrayList<>();
			for (TRADABLE t : Lists.of(TR.ALL()))
				keys.add(t.key());
			return Commands.m("usage", "gift <faction> <credits|key> <n>", "keys", keys);
		}
		FactionNPC f = npc(a[1]);
		int n = Commands.i(a, 3);
		Deal d = new Deal();
		d.setFactionAndClear(f);
		DealParty p = n >= 0 ? d.player : d.npc;
		Faction giver = n >= 0 ? FACTIONS.player() : f;
		int amount = Math.abs(n);
		if (a[2].equalsIgnoreCase("credits")) {
			p.credits.set(amount); // clamped to what the giver has (DealParty.java:32)
			amount = p.credits.get();
		} else {
			TRADABLE t = null;
			for (TRADABLE x : Lists.of(TR.ALL()))
				if (x.key().equalsIgnoreCase(a[2]))
					t = x;
			if (t == null)
				throw new IllegalArgumentException("unknown resource key '" + a[2] + "' (gift with no args lists them)");
			amount = Math.min(amount, Math.max(giver.res().getAvailable(t), 0));
			p.resources.set(t, amount);
		}
		double op = d.execute(true);
		return Commands.m("faction", "" + f.name, "given", amount, "by", n >= 0 ? "player" : "faction", "opinionChange", op, "opinion", ROPINION.get(f));
	}

	// ---- regions / paths ----------------------------------------------------------------------

	// game path: view/world/ui/region/PlayDebug.java:55 (claim checkbox: RD.setFaction), world/map/regions/Region.java
	private static Object region(String[] a) {
		if (a.length == 1 || a[1].equalsIgnoreCase("list")) {
			Faction only = null;
			boolean none = a.length > 2 && a[2].equalsIgnoreCase("none");
			if (a.length > 2 && !none)
				only = fac(a[2]);
			List<Object> l = new ArrayList<>();
			for (Region r : Lists.of(WORLD.REGIONS().all()))
				if (r.active() && (a.length < 3 || r.faction() == only))
					l.add(reg1(r));
			return l;
		}
		if (a[1].equalsIgnoreCase("claim")) {
			Region r = reg(a[2]);
			Faction f = a.length > 3 && a[3].equalsIgnoreCase("none") ? null : (a.length > 3 ? fac(a[3]) : FACTIONS.player());
			if (r.capitol() && r.faction() instanceof FactionNPC)
				FACTIONS.remove((FactionNPC) r.faction(), true);
			RD.setFaction(r, f, true);
			return reg1(r);
		}
		Region r = reg(a[1]);
		Map<String, Object> m = reg1(r);
		m.put("area", r.info.area());
		m.put("besieged", r.besieged());
		m.put("reachable", RD.DIST().reachable(r));
		m.put("borderingPlayer", RD.DIST().neighbours(r));
		m.put("distanceFromPlayer", RD.DIST().distance().get(r));
		return m;
	}

	// game path: world/map/regions via RD.DIST() (world/region/RDDistance.java:285 tradePartners, :296 neighs); WRegFinder.all (world/map/pathing/WRegFinder.java:32)
	private static Object wpath(String[] a) {
		if (a.length < 3) {
			List<Object> tp = new ArrayList<>();
			for (RegDist r : RD.DIST().tradePartners(FACTIONS.player()))
				tp.add(Commands.m("region", r.reg.index(), "faction", "" + r.reg.faction().name, "distance", r.distance, "water", r.water));
			List<Object> nb = new ArrayList<>();
			for (FactionNPC f : Lists.of(RD.DIST().neighs()))
				nb.add("" + f.name);
			return Commands.m("playerCapitol", FACTIONS.player().capitolRegion().index(), "neighbours", nb, "tradePartners", tp);
		}
		Region from = reg(a[1]), to = reg(a[2]);
		Object diplomatic = null;
		for (RegDist r : WORLD.PATH().regFinder.all(from, Treaty.FACTION_REACHABLE, WRegSel.DUMMY()))
			if (r.reg == to)
				diplomatic = r.distance;
		return Commands.m("distance", WORLD.PATH().distance(from, to), "reachableUnderTreaties", diplomatic != null, "treatyDistance", diplomatic);
	}

	// ---- raids --------------------------------------------------------------------------------

	// game path: game/raiding/RAIDING.java:150 raid() (event flow), :116 "RAIDER appear cap" debug, :126 "RAIDER clear" debug
	private static Object raid(String[] a) {
		String c = a.length > 1 ? a[1].toLowerCase() : "";
		switch (c) {
		case "":
			Raider cur = GAME.raiders().current.current();
			return Commands.m("activeRaiders", GAME.raiders().active().size(), "current", cur == null ? null : cur.name, "invading", SETT.INVADOR().invading());
		case "demand":
			GAME.raiders().raid();
			return Commands.m("started", GAME.raiders().current.current() != null);
		case "now": {
			Region cap = FACTIONS.player().capitolRegion();
			double power = GAME.raiders().util.defences(cap) * (a.length > 2 ? Double.parseDouble(a[2]) : 1.25);
			Raider r = new Raider(RACES.playable().rnd(), power);
			r.adjust(FACTIONS.WORTH().raider(), power, RND.rFloat());
			r.text.set(r, true);
			boolean ok = GAME.raiders().current.appear(r, FACTIONS.player().cx(), FACTIONS.player().cy());
			return Commands.m("appeared", ok, "raider", r.name, "men", r.army.men, "power", r.army.power, "invading", SETT.INVADOR().invading());
		}
		case "clear":
			GAME.raiders().current.clear();
			return Commands.m("cleared", true);
		default:
			throw new IllegalArgumentException("raid | raid demand | raid now [powerMul] | raid clear");
		}
	}

	// ---- armies / caravans --------------------------------------------------------------------

	// game path: world/entity/army/WArmyConstructor.java:100 "ArmyEnemyFaction" debug tool (create + AD.regional().create + fillAll); disband: game/faction/FACTIONS.java:307 remove()
	private static Object army(String[] a) {
		String c = a.length > 1 ? a[1].toLowerCase() : "list";
		if (c.equals("list")) {
			List<Object> l = new ArrayList<>();
			for (Faction f : Lists.of(FACTIONS.active())) {
				if (a.length > 2 && f != fac(a[2]))
					continue;
				for (WArmy w : Lists.of(f.armies().all()))
					l.add(Commands.m("faction", "" + f.name, "name", "" + w.name, "men", AD.men(null).get(w), "state", "" + w.state().name(w),
							"region", w.region() == null ? -1 : w.region().index()));
			}
			return l;
		}
		if (a.length < 3)
			throw new IllegalArgumentException("army list [<faction>] | army spawn <faction> [divs] [region] | army clear <faction>");
		Faction f = fac(a[2]);
		if (c.equals("clear")) {
			f.armies().disbandAll();
			return Commands.m("cleared", "" + f.name);
		}
		if (!c.equals("spawn"))
			throw new IllegalArgumentException("army list|spawn|clear");
		int divs = a.length > 3 ? Commands.i(a, 3) : 10;
		Region home = a.length > 4 ? reg(a[4]) : f.capitolRegion();
		if (!f.armies().canCreate())
			throw new IllegalArgumentException("faction cannot field more armies");
		COORDINATE xy = WORLD.PATH().rnd(home);
		if (xy == null)
			throw new IllegalArgumentException("no free tile in region " + home.index());
		WArmy e = WORLD.ENTITIES().armies.create(xy.x(), xy.y(), f);
		for (int i = 0; i < divs && e.divs().canAdd(); i++) {
			WDivRegional d = AD.regional().create(RACES.all().rnd(), 0.25 + 0.75 * RND.rFloat(), e);
			d.randomize(RND.rFloat(), RND.rFloat());
			d.menSet(d.menTarget());
		}
		AD.supplies().fillAll(e);
		AD.updateArmy(e);
		return Commands.m("faction", "" + f.name, "divisions", e.divs().size(), "men", AD.men(null).get(e), "x", xy.x(), "y", xy.y());
	}

	// game path: world/entity/caravan/Shipments.java:62 create (used by DealParty.execute for gifts)
	private static Object caravan(String[] a) {
		Region from = reg(a.length > 1 ? a[1] : ""), to = reg(a.length > 2 ? a[2] : "");
		if (to.faction() == null)
			throw new IllegalArgumentException("destination region has no owner");
		TRADE_TYPE type = TRADE_TYPE.diplomacy;
		List<Object[]> load = new ArrayList<>();
		for (int i = 3; i < a.length; i++) {
			TRADE_TYPE t = a[i].equals("trade") ? TRADE_TYPE.trade : a[i].equals("tax") ? TRADE_TYPE.tax
					: a[i].equals("spoils") ? TRADE_TYPE.spoils : a[i].equals("diplomacy") ? TRADE_TYPE.diplomacy : null;
			if (t != null) {
				type = t;
				continue;
			}
			load.add(new Object[] { tradable(a[i]), Commands.i(a, ++i) });
		}
		world.entity.caravan.Shipment sh = WORLD.ENTITIES().caravans.create(from, to, type);
		if (sh == null)
			return Commands.m("created", false, "why", "no world path between the regions, or no room for more world entities");
		for (Object[] l : load)
			sh.load((TRADABLE) l[0], (Integer) l[1]); // Shipment.java:237; on arrival it goes to the destination's buyer
		return Commands.m("created", true, "x", sh.ctx(), "y", sh.cty(), "to", to.info.name().toString(), "type", type.index);
	}

	private static TRADABLE tradable(String k) {
		for (TRADABLE x : Lists.of(TR.ALL()))
			if (x.key().equalsIgnoreCase(k) || x.key().equalsIgnoreCase("RES_" + k))
				return x;
		throw new IllegalArgumentException("unknown resource key '" + k + "' (gift with no args lists them)");
	}

	// game path: game/faction/diplomacy/UpVassal.java (MOverlord message: FWorth.vassal per vassal, then the Accept button)
	private static Object vassal(String[] a) {
		String c = a.length > 1 ? a[1].toLowerCase() : "list";
		if (c.equals("list")) {
			List<Object> l = new ArrayList<>();
			for (Faction f : Lists.of(FACTIONS.active())) {
				Faction o = DIP.overlord(f);
				if (o != null)
					l.add(Commands.m("vassal", "" + f.name, "vassalIdx", f.index(), "overlord", "" + o.name, "overlordIdx", o.index()));
			}
			return l;
		}
		if (!c.equals("tribute"))
			throw new IllegalArgumentException("vassal list | vassal tribute [accept|decline]");
		boolean accept = a.length < 3 || !a[2].equalsIgnoreCase("decline");
		List<Object> l = new ArrayList<>();
		double before = FACTIONS.player().credits().getD();
		for (Faction f : Lists.of(DIP.VASSAL().all(FACTIONS.player()))) {
			FactionNPC n = (FactionNPC) f;
			DIP.TMP().setFactionAndClear(n);
			int cr = (int) game.faction.FWorth.vassal(n);
			if (accept)
				FACTIONS.player().credits().inc(cr, game.faction.FCredits.CTYPE.TRIBUTE);
			ROPINION.OTHER().acceptTribute(n, accept);
			l.add(Commands.m("vassal", "" + n.name, "tribute", cr, "opinion", ROPINION.get(n)));
		}
		return Commands.m("accepted", accept, "vassals", l, "creditsBefore", before, "creditsAfter", FACTIONS.player().credits().getD());
	}

	/** Event.all is package-private (read-only access by reflection; firing goes through the public EVENT_HANDLER.set). */
	@SuppressWarnings("unchecked")
	private static Iterable<Event> events() {
		try {
			java.lang.reflect.Field f = Event.class.getDeclaredField("all");
			f.setAccessible(true);
			return (Iterable<Event>) f.get(null);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	// ---- game events --------------------------------------------------------------------------

	// game path: game/event/engine/UIEventDebug.java:150 (click an event: en.set(a,false,false,true,true)), "Expire" button :74 en.expire()
	private static Object gevent(String[] a) {
		if (a.length == 1) {
			List<Object> l = new ArrayList<>();
			Object[] datas = vizardalpha.polyphon.api.Shadow.get(GAME.EVENT(), "datas"); // EVENT_HANDLER per-event counters
			for (Event e : events())
				l.add(Commands.m("key", e.key, "name", "" + e.info.name, "canFire", GAME.EVENT().can(e),
					"fired", vizardalpha.polyphon.api.Shadow.get(datas[e.allIndex], "fired")));
			Event cur = GAME.EVENT().current();
			return Commands.m("current", cur == null ? null : cur.key, "events", l);
		}
		if (a[1].equalsIgnoreCase("expire")) {
			GAME.EVENT().expire();
			return Commands.m("expired", true);
		}
		for (Event e : events())
			if (e.key.equalsIgnoreCase(a[1])) {
				Object[] datas = vizardalpha.polyphon.api.Shadow.get(GAME.EVENT(), "datas");
				int before = vizardalpha.polyphon.api.Shadow.get(datas[e.allIndex], "fired");
				GAME.EVENT().set(e, false, false, true, true);
				int after = vizardalpha.polyphon.api.Shadow.get(datas[e.allIndex], "fired");
				Event cur = GAME.EVENT().current();
				return Commands.m("fired", e.key, "timesFiredBefore", before, "timesFiredAfter", after, "currentNow", cur == null ? null : cur.key,
					"message", GAME.EVENT().mess() != null);
			}
		throw new IllegalArgumentException("no event '" + a[1] + "' (gevent lists them)");
	}

	private ModWorld() {}
}
