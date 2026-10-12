package lordapcom.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import game.GAME;
import game.battle.Army;
import game.battle.div.Div;
import game.battle.formation.DIV_FORMATION;
import game.battle.formation.DivFormationImp;
import game.battle.thread.order.BattleOrderTask;
import game.faction.FACTIONS;
import game.faction.Faction;
import init.constant.C;
import init.constant.Config;
import init.race.RACES;
import init.race.Race;
import init.type.HTYPE;
import init.type.HTYPES;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.stats.STATS;
import settlement.stats.colls.StatsBattle.StatTraining;
import settlement.stats.equip.EquipBattle;
import snake2d.PathTile;
import snake2d.util.datatypes.COORDINATE;
import view.ui.div.UIDivCardWorld;
import world.WORLD;
import world.army.ADDiv;
import world.army.AD;
import world.entity.WEntity;
import world.entity.army.WArmy;
import world.entity.army.WArmyState;
import world.map.regions.Region;

/**
 * The player's own troops: settlement divisions (view/sett/ui/army, view/battle) and world-map armies (view/world/ui/army).
 * Orders go through the same calls the battle / world UI makes (BattleOrderTask, DivDeployerUser, DivSettings, WArmy).
 */
final class ModMilitary {

	static void register() {
		Registry.add("div", "div [list [all|enemy]] | div keys | div create <race> <men> [k=v..] | div set <i> [men=N] [race=key] [k=v..] | div spawn <race> <men> <x> <y> [k=v..]"
			+ " | div disband <sel> | div move <sel> <x> <y> [x2 y2] | div attack <sel> <enemyDiv>|tile <x> <y> [melee] | div charge <sel> | div stop <sel>"
			+ " | div opt <sel> <muster|mop|guard|run|fire|hold|duty|loose|tight> [on|off] | div send <sel> <worldArmy> | div recall <sel>"
			+ " - settlement divisions of the player (sel: all | 0,2,3 | 1-4); k=v are equipment/training keys (see div keys) with value 0..1", ModMilitary::div);
		Registry.add("warmy", "warmy [list] | warmy go <i> <x> <y> | warmy besiege <i> <region> | warmy intercept <i> <faction> <j> | warmy stop <i>"
			+ " | warmy raid <i> [on|off] | warmy home <i> | warmy disband <i> - world-map armies: the player's, or another kingdom's with f=<faction> anywhere (its AI may override) (<i> = index in 'warmy list')", ModMilitary::warmy);
	}

	// ---- helpers ------------------------------------------------------------------------------

	private static Army army() {
		return GAME.ARMIES().player();
	}

	private static Race race(String s) {
		for (Race r : Lists.of(RACES.all()))
			if (r.key.equalsIgnoreCase(s))
				return r;
		throw new IllegalArgumentException("unknown race '" + s + "' (div keys)");
	}

	private static Div div(int i) {
		if (i < 0 || i >= army().divisions().size())
			throw new IllegalArgumentException("division index 0.." + (army().divisions().size() - 1));
		return army().divisions().get(i);
	}

	/** all | 0,2,3 | 1-4 -> player divisions (army-local index). */
	private static List<Div> sel(String s) {
		List<Div> l = new ArrayList<>();
		if (s.equalsIgnoreCase("all")) {
			for (Div d : Lists.of(army().divisions()))
				if (d.menNrOf() > 0)
					l.add(d);
			return l;
		}
		for (String p : s.split(",")) {
			String[] r = p.split("-");
			int lo = Integer.parseInt(r[0]), hi = r.length > 1 ? Integer.parseInt(r[1]) : lo;
			for (int i = lo; i <= hi; i++)
				l.add(div(i));
		}
		return l;
	}


	/** k=v args from index 'from' on: equipment / training keys, value 0..1 (what the UIDivEditor sliders hold). */
	private static void applyKv(Div d, String[] a, int from) {
		for (int i = from; i < a.length; i++) {
			String[] kv = a[i].split("=", 2);
			if (kv.length != 2)
				throw new IllegalArgumentException("expected key=value, got " + a[i]);
			String k = kv[0];
			switch (k.toLowerCase()) {
			case "men": d.info.menSet(Integer.parseInt(kv[1])); continue;
			case "race": d.info.raceSet(race(kv[1])); continue;
			case "name": d.info.name().clear().add(kv[1].replace('_', ' ')); continue;
			default:
			}
			double v = Double.parseDouble(kv[1]);
			boolean ok = false;
			for (EquipBattle e : Lists.of(STATS.EQUIP().BATTLE_ALL()))
				if (e.key().equalsIgnoreCase(k) || e.eKey.equalsIgnoreCase(k)) {
					d.info.equipSet(e, v);
					ok = true;
				}
			for (StatTraining t : Lists.of(STATS.BATTLE().TRAINING_ALL))
				if (t.key().equalsIgnoreCase(k)) {
					d.info.trainingSet(t, v);
					ok = true;
				}
			if (!ok)
				throw new IllegalArgumentException("unknown equipment/training key '" + k + "' (div keys)");
		}
	}

	private static Map<String, Object> describe(Div d, boolean enemy) {
		Map<String, Object> r = Commands.m("i", d.indexArmy(), "name", d.info.name().toString(), "race", d.race().key, "men", d.menNrOf(),
			"target", d.info.men(), "morale", d.morale());
		Map<String, Object> eq = Commands.m();
		for (EquipBattle e : Lists.of(STATS.EQUIP().BATTLE_ALL()))
			if (e.target(d) > 0 || e.stat().div().get(d) > 0)
				eq.put(e.key(), Commands.m("target", e.target(d), "max", e.max(), "equipped", e.stat().div().get(d)));
		r.put("equip", eq);
		Map<String, Object> tr = Commands.m();
		for (StatTraining t : Lists.of(STATS.BATTLE().TRAINING_ALL))
			if (d.info.training(t) > 0)
				tr.put(t.key(), d.info.training(t));
		r.put("training", tr);
		r.put("experience", d.info.experience());
		if (d.menNrOf() > 0) {
			r.put("x", d.centre().cX() >> C.T_SCROLL);
			r.put("y", d.centre().cY() >> C.T_SCROLL);
			r.put("fighting", d.status().isFighting());
		}
		DivFormationImp f = new DivFormationImp();
		d.order().dest.get(f);
		COORDINATE c = f.deployed() > 0 ? f.centreTile() : null;
		if (enemy) {
			r.put("dest", c == null ? null : Commands.m("x", c.x(), "y", c.y(), "deployed", f.deployed()));
			return r;
		}
		BattleOrderTask t = new BattleOrderTask();
		d.order().task.get(t);
		r.put("order", t.task().name());
		if (t.targetDiv() != null)
			r.put("orderTargetDiv", t.targetDiv().indexArmy());
		if (t.targetTileX() >= 0)
			r.put("orderTargetTile", Commands.m("x", t.targetTileX(), "y", t.targetTileY()));
		r.put("dest", c == null ? null : Commands.m("x", c.x(), "y", c.y(), "deployed", f.deployed()));
		var s = d.settings();
		r.put("mustered", s.mustering());
		r.put("guard", s.guard);
		r.put("run", s.running);
		r.put("formation", s.formation.name());
		r.put("fireAtWill", s.fireAtWill);
		r.put("holdToFire", s.shouldNotMoveToFire);
		r.put("mopping", s.moppingUp());
		r.put("ranged", s.ammo() != null);
		r.put("dutyGuard", SETT.ROOMS().GUARD.activeDuty.is(d));
		WArmy w = AD.cityDivs().attachedArmy(d);
		r.put("worldArmy", w == null ? null : w.name.toString());
		return r;
	}

	// ---- settlement divisions -----------------------------------------------------------------

	private static Object div(String[] a) {
		String c = a.length > 1 ? a[1].toLowerCase() : "list";
		switch (c) {
		case "list": {
			boolean all = a.length > 2 && a[2].equals("all"), enemy = a.length > 2 && a[2].equals("enemy");
			List<Object> l = new ArrayList<>();
			for (Div d : Lists.of((enemy ? GAME.ARMIES().enemy() : army()).divisions()))
				if (all || d.menNrOf() > 0 || d.info.men() > 0)
					l.add(describe(d, enemy));
			return Commands.m("menTotal", (enemy ? GAME.ARMIES().enemy() : army()).men(), "divisions", l);
		}
		case "keys": {
			List<Object> eq = new ArrayList<>(), tr = new ArrayList<>(), rc = new ArrayList<>();
			for (EquipBattle e : Lists.of(STATS.EQUIP().BATTLE_ALL()))
				eq.add(Commands.m("key", e.key(), "max", e.max()));
			for (StatTraining t : Lists.of(STATS.BATTLE().TRAINING_ALL))
				tr.add(t.key());
			for (Race r : Lists.of(RACES.all()))
				rc.add(r.key);
			return Commands.m("equip", eq, "training", tr, "races", rc, "menPerDivision", Config.battle().MEN_PER_DIVISION,
				"divisions", army().divisions().size(), "formations", java.util.Arrays.toString(DIV_FORMATION.values()));
		}
		case "create": return create(a);
		case "set": {
			Div d = div(Commands.i(a, 2));
			applyKv(d, a, 3);
			return describe(d, false);
		}
		case "spawn": return spawn(a);
		case "disband": return disband(a);
		case "move": return move(a);
		case "attack": return attack(a);
		case "charge":
		case "stop": return charge(a, c.equals("charge"));
		case "opt": return opt(a);
		case "send": return send(a);
		case "recall": return recall(a);
		default:
			throw new IllegalArgumentException("div list|keys|create|set|spawn|disband|move|attack|charge|stop|opt|send|recall");
		}
	}

	// game path: view/sett/ui/army/Actions.java:90 "Create" button (getNextEmptyOrdered, raceSet, menSet, equip/training 0) then Edit.java:76 "Accept" (editor.copyChanges)
	private static Object create(String[] a) {
		if (a.length < 4)
			throw new IllegalArgumentException("div create <race> <men> [equipKey=0..1] [trainingKey=0..1]");
		Div d = army().getNextEmptyOrdered();
		if (d == null)
			throw new IllegalStateException("no free division slot (div disband first)");
		d.info.raceSet(race(a[2]));
		d.info.menSet(Commands.i(a, 3));
		for (EquipBattle e : Lists.of(STATS.EQUIP().BATTLE_ALL()))
			e.targetSet(d, 0);
		for (StatTraining t : Lists.of(STATS.BATTLE().TRAINING_ALL))
			d.info.trainingSet(t, 0);
		applyKv(d, a, 4);
		return describe(d, false); // citizens are conscripted by the game over time: men grows towards target
	}

	// game path: view/sett/ui/army/FormationDebugPlacer.java:79 place + :56 afterPlaced (sett debug panel "Place Division"); equipment/training set per man like :87-96
	private static Object spawn(String[] a) {
		if (a.length < 6)
			throw new IllegalArgumentException("div spawn <race> <men> <x> <y> [equipKey=0..1] [trainingKey=0..1]");
		Race race = race(a[2]);
		int men = Math.min(Commands.i(a, 3), Config.battle().MEN_PER_DIVISION), tx = Commands.i(a, 4), ty = Commands.i(a, 5);
		Div d = army().getNextEmptyOrdered();
		if (d == null)
			throw new IllegalStateException("no free division slot (div disband first)");
		d.info.raceSet(race);
		for (EquipBattle e : Lists.of(STATS.EQUIP().BATTLE_ALL()))
			e.targetSet(d, 0);
		for (StatTraining t : Lists.of(STATS.BATTLE().TRAINING_ALL))
			d.info.trainingSet(t, 0);
		applyKv(d, a, 6);
		int w = (int) Math.ceil(Math.sqrt(men)), h = (int) Math.ceil((double) men / w);
		HTYPE type = HTYPES.SUBJECT();
		d.info.menSet(Config.battle().MEN_PER_DIVISION);
		d.settings().musteringSet(true);
		int placed = 0;
		for (int y = ty; y < ty + h && placed < men; y++)
			for (int x = tx; x < tx + w && placed < men; x++) {
				if (!SETT.IN_BOUNDS(x, y) || SETT.PATH().solidity.is(x, y) || SETT.ENTITIES().hasAtTile(x, y))
					continue;
				Humanoid hu = new Humanoid(x * C.TILE_SIZE + C.TILE_SIZEH, y * C.TILE_SIZE + C.TILE_SIZEH, race, type, null);
				for (StatTraining t : Lists.of(STATS.BATTLE().TRAINING_ALL))
					t.stat.indu().setD(hu.indu(), d.info.training(t));
				for (EquipBattle e : Lists.of(STATS.EQUIP().BATTLE_ALL()))
					e.stat().indu().setD(hu.indu(), d.info.equip(e));
				STATS.BATTLE().COMBAT_EXPERIENCE.indu().setD(hu.indu(), d.info.experience());
				STATS.BATTLE().basicTraining.setD(hu.indu(), 1.0);
				hu.setDivision(d);
				placed++;
			}
		d.info.menSet(placed);
		snake2d.util.sets.ArrayList<Div> divs = new snake2d.util.sets.ArrayList<>(1);
		divs.add(d);
		int x1 = tx * C.TILE_SIZE + C.TILE_SIZEH, x2 = (tx + w) * C.TILE_SIZE + C.TILE_SIZEH, y1 = ty * C.TILE_SIZE + C.TILE_SIZEH;
		GAME.ARMIES().placer.deploy(divs, x1, x2, y1, y1);
		GAME.ARMIES().initAndTeleport(divs);
		Map<String, Object> r = describe(d, false);
		r.put("placed", placed);
		return r;
	}

	// game path: view/sett/ui/army/Actions.java:360 "Disband" (only divisions not attached to a world army: div.info.menSet(0))
	private static Object disband(String[] a) {
		int n = 0;
		for (Div d : sel(a.length > 2 ? a[2] : ""))
			if (AD.cityDivs().attachedArmy(d) == null) {
				d.info.menSet(0);
				n++;
			}
		return Commands.m("disbanded", n);
	}

	// game path: view/battle/BattlePlacerPlace.java:46 (click-drag on the ground) -> game/battle/formation/DivDeployerUser.java:80 deploy(divs,x1,x2,y1,y2)
	private static Object move(String[] a) {
		if (a.length < 5)
			throw new IllegalArgumentException("div move <sel> <x> <y> [x2 y2] - line from (x,y) to (x2,y2); default: a square-ish block per division");
		List<Div> l = sel(a[2]);
		int x1 = Commands.i(a, 3) * C.TILE_SIZE + C.TILE_SIZEH, y1 = Commands.i(a, 4) * C.TILE_SIZE + C.TILE_SIZEH, x2, y2 = y1;
		if (a.length > 6) {
			x2 = Commands.i(a, 5) * C.TILE_SIZE + C.TILE_SIZEH;
			y2 = Commands.i(a, 6) * C.TILE_SIZE + C.TILE_SIZEH;
		} else {
			int len = 0; // a drag shorter than the divisions' footprint deploys nothing, so size it like a user would
			for (Div d : l)
				len += (int) Math.ceil(Math.sqrt(d.menNrOf())) * DIV_FORMATION.TIGHT.size(d) * 3 / 2;
			x2 = x1 + Math.max(len, C.TILE_SIZE * 2);
		}
		snake2d.util.sets.ArrayList<Div> divs = new snake2d.util.sets.ArrayList<>(Math.max(1, l.size()));
		for (Div d : l)
			divs.add(d);
		boolean ok = GAME.ARMIES().placer.deploy(divs, x1, x2, y1, y2);
		return Commands.m("deployed", ok, "divisions", l.size());
	}

	// game path: view/battle/BattlePlacerAttack.java:150 (div target: attackRanged/attackMelee) and :255/:318 (building: task.attack) then dd.order().task.set(task)
	private static Object attack(String[] a) {
		if (a.length < 4)
			throw new IllegalArgumentException("div attack <sel> <enemyDiv> [melee] | div attack <sel> tile <x> <y>");
		List<Div> l = sel(a[2]);
		BattleOrderTask task = new BattleOrderTask();
		if (a[3].equals("tile")) {
			int tx = Commands.i(a, 4), ty = Commands.i(a, 5);
			for (Div d : l) {
				task.attack(tx, ty, d);
				d.order().task.set(task);
			}
			return Commands.m("attackingTile", Commands.m("x", tx, "y", ty), "divisions", l.size());
		}
		int ei = Integer.parseInt(a[3]);
		if (ei < 0 || ei >= GAME.ARMIES().enemy().divisions().size())
			throw new IllegalArgumentException("enemy division index (div list enemy)");
		Div target = GAME.ARMIES().enemy().divisions().get(ei);
		if (target.menNrOf() == 0)
			throw new IllegalArgumentException("enemy division " + ei + " has no men");
		boolean melee = a.length > 4 && a[4].equals("melee");
		int ranged = 0;
		for (Div d : l) {
			if (!melee && d.settings().ammo() != null) {
				task.attackRanged(target, d);
				ranged++;
			} else
				task.attackMelee(target, d);
			d.order().task.set(task);
		}
		return Commands.m("target", ei, "divisions", l.size(), "ranged", ranged);
	}

	// game path: view/battle/UISelection.java:289 charge() and :370 stop button / BattlePlacer.java:101 BACKSPACE (task.charge|stop + order().task.set)
	private static Object charge(String[] a, boolean charge) {
		List<Div> l = sel(a.length > 2 ? a[2] : "");
		BattleOrderTask task = new BattleOrderTask();
		for (Div d : l) {
			if (charge)
				task.charge(d);
			else
				task.stop(d);
			d.order().task.set(task);
		}
		return Commands.m(charge ? "charging" : "stopped", l.size());
	}

	/** Toggles, applied exactly like the selection panel buttons (view/battle/UISelection.java). */
	private static Object opt(String[] a) {
		if (a.length < 4)
			throw new IllegalArgumentException("div opt <sel> <muster|mop|guard|run|fire|hold|duty|loose|tight> [on|off]");
		List<Div> l = sel(a[2]);
		String o = a[3].toLowerCase();
		boolean on = a.length < 5 || !a[4].equalsIgnoreCase("off");
		int n = 0;
		for (Div d : l) {
			var s = d.settings();
			switch (o) {
			case "muster": // UISelection.java:646 (stand down = off: men return to civil life)
				if (!on)
					s.musteringSet(false);
				else if (d.menNrOf() > 0 && hasPosition(d))
					s.musteringSet(true);
				else
					continue;
				break;
			case "mop": // :407
				if (d.menNrOf() > 0 && hasPosition(d))
					s.moppingSet(on);
				else
					continue;
				break;
			case "guard": s.guard = on; break; // :274
			case "run": s.running = on; break; // :280
			case "fire": s.fireAtWill = on; break; // :448
			case "hold": s.shouldNotMoveToFire = on; break; // :478
			case "duty": SETT.ROOMS().GUARD.activeDuty.set(d, on); break; // view/sett/ui/army/Actions.java:285
			case "loose": s.formation = DIV_FORMATION.LOOSE; break; // :265
			case "tight": s.formation = DIV_FORMATION.TIGHT; break; // :270
			default: throw new IllegalArgumentException("unknown option " + o);
			}
			n++;
		}
		return Commands.m("applied", n, "of", l.size());
	}

	private static boolean hasPosition(Div d) {
		DivFormationImp f = new DivFormationImp();
		d.order().dest.get(f);
		return f.deployed() > 0 || d.position().deployed() > 0;
	}

	// game path: view/sett/ui/army/Actions.java:160 "Send Out" (UIPickerArmy.pick -> AD.cityDivs().attach(army, div))
	private static Object send(String[] a) {
		if (a.length < 4)
			throw new IllegalArgumentException("div send <sel> <worldArmyIdx>");
		WArmy w = armyAt(Commands.i(a, 3));
		if (SETT.ENTRY().isClosed())
			throw new IllegalStateException("city is closed");
		int n = 0;
		for (Div d : sel(a[2]))
			if (w.divs().canAdd() && AD.cityDivs().attachedArmy(d) == null && UIDivCardWorld.supplyError(d) == null) {
				AD.cityDivs().attach(w, d);
				n++;
			}
		return Commands.m("sent", n);
	}

	// game path: view/sett/ui/army/Actions.java:222 "Recall" (AD.cityDivs().attach(null, div))
	private static Object recall(String[] a) {
		int n = 0;
		for (Div d : sel(a.length > 2 ? a[2] : "")) // 'all' only lists divisions with men; attached ones keep men in the world army
			if (AD.cityDivs().attachedArmy(d) != null) {
				AD.cityDivs().attach(null, d);
				n++;
			}
		return Commands.m("recalled", n);
	}

	// ---- world armies -------------------------------------------------------------------------

	private static WArmy armyAt(int i) {
		snake2d.util.sets.LIST<WArmy> all = (owner == null ? FACTIONS.player() : owner).armies().all();
		if (i < 0 || i >= all.size())
			throw new IllegalArgumentException("no army #" + i + " (warmy [f=<faction>] list; make one with 'army spawn <faction>')");
		return all.get(i);
	}

	private static Map<String, Object> describe(WArmy w, int i) {
		Map<String, Object> r = Commands.m("i", i, "name", w.name.toString(), "men", AD.men(null).get(w), "menTarget", AD.menTarget(null).get(w),
			"state", w.state().name(w).toString(), "x", w.ctx(), "y", w.cty(), "region", w.region() == null ? -1 : w.region().index(),
			"raiding", w.raiding(), "moving", w.path().moving(w.body()));
		if (w.path().isValid())
			r.put("dest", Commands.m("x", w.path().destX(), "y", w.path().destY()));
		Region b = w.besieging();
		if (b != null)
			r.put("besieging", b.index());
		WArmy t = w.intercepting();
		if (t != null)
			r.put("intercepting", "" + t.name);
		List<Object> ds = new ArrayList<>();
		for (int k = 0; k < w.divs().size(); k++) {
			ADDiv d = w.divs().get(k);
			ds.add(Commands.m("type", d.getClass().getSimpleName(), "race", d.race().key, "men", d.men(), "target", d.menTarget()));
		}
		r.put("divisions", ds);
		return r;
	}

	private static Faction owner = null; // warmy f=<faction>: the same orders on another kingdom's armies

	private static Object warmy(String[] in) {
		List<String> args = new ArrayList<>();
		owner = FACTIONS.player();
		for (String x : in)
			if (x.startsWith("f="))
				owner = faction(x.substring(2));
			else
				args.add(x);
		String[] a = args.toArray(new String[0]);
		String c = a.length > 1 ? a[1].toLowerCase() : "list";
		snake2d.util.sets.LIST<WArmy> all = owner.armies().all();
		if (c.equals("list")) {
			List<Object> l = new ArrayList<>();
			for (int i = 0; i < all.size(); i++)
				l.add(describe(all.get(i), i));
			return Commands.m("faction", "" + owner.name, "armies", l, "canCreateMore", owner.armies().canCreate());
		}
		WArmy w = armyAt(Commands.i(a, 2));
		switch (c) {
		case "go": return go(w, Commands.i(a, 3), Commands.i(a, 4));
		case "besiege": { // view/world/ui/army/ToolMove.java:68 + :114 (Army.besiege)
			Region r = region(a, 3);
			if (!WArmyState.canBesiege(w, r))
				throw new IllegalStateException("cannot besiege region " + r.index() + " (not at war / own region)");
			if (w.besigeTile(r) == null)
				throw new IllegalStateException("region unreachable");
			w.besiege(r);
			return describe(w, Commands.i(a, 2));
		}
		case "intercept": { // ToolMove.java:42-52 (attack when at war, intercept otherwise)
			Faction f = faction(a[3]);
			snake2d.util.sets.LIST<WArmy> fa = f.armies().all();
			int j = Commands.i(a, 4);
			if (j < 0 || j >= fa.size())
				throw new IllegalArgumentException("army index 0.." + (fa.size() - 1) + " of that faction (army list <faction>)");
			if (AD.men(null).get(w) == 0)
				throw new IllegalStateException("army has no men");
			w.intercept(fa.get(j));
			return describe(w, Commands.i(a, 2));
		}
		case "stop": // view/world/ui/army/Army.java:168
			w.stop();
			return describe(w, Commands.i(a, 2));
		case "raid": // Army.java:182
			w.raid(a.length > 3 ? !a[3].equalsIgnoreCase("off") : !w.raiding());
			return describe(w, Commands.i(a, 2));
		case "home": { // no dedicated button: the player sends the army to the capital with the move tool
			Region cap = owner.capitolRegion();
			if (cap == null)
				throw new IllegalStateException("no capitol region");
			return go(w, cap.cx(), cap.cy());
		}
		case "disband": // Army.java:200 (button only active when the army has no men target)
			if (AD.menTarget(null).get(w) != 0)
				throw new IllegalStateException("army still has men; the UI only allows disbanding empty armies");
			w.disband();
			return Commands.m("disbanded", true);
		default:
			throw new IllegalArgumentException("warmy list|go|besiege|intercept|stop|raid|home|disband");
		}
	}

	/** Same decision tree as the move tool's isPlacable + place (view/world/ui/army/ToolMove.java:30-135). */
	private static Object go(WArmy w, int tx, int ty) {
		if (AD.men(null).get(w) == 0)
			throw new IllegalStateException("army has no men");
		if (w.ctx() == tx && w.cty() == ty)
			return Commands.m("already", true);
		if (!WORLD.FOW().is(tx, ty)) {
			WArmy other = null;
			for (WEntity e : WORLD.ENTITIES().fill(tx * C.TILE_SIZE + C.TILE_SIZEH, ty * C.TILE_SIZE + C.TILE_SIZEH))
				if (e instanceof WArmy && e != w) {
					other = (WArmy) e;
					break;
				}
			if (other != null) {
				if (WORLD.PATH().path(w.ctx(), w.cty(), tx, ty, w.path().treaty()) == null)
					throw new IllegalStateException("unreachable");
				w.intercept(other);
				return Commands.m("intercepting", "" + other.name);
			}
		}
		Region reg = WORLD.REGIONS().centre.get(tx, ty);
		if (reg != null && WArmyState.canBesiege(w, reg)) {
			if (w.besigeTile(reg) == null)
				throw new IllegalStateException("unreachable");
			w.besiege(reg);
			return Commands.m("besieging", reg.index());
		}
		PathTile p = WORLD.PATH().path(w.ctx(), w.cty(), tx, ty, w.path().treaty());
		if (p == null)
			throw new IllegalStateException("unreachable");
		w.setDestination(tx, ty);
		return Commands.m("moving", true, "state", w.state().name(w).toString());
	}

	private static Region region(String[] a, int idx) {
		int i = Commands.i(a, idx);
		if (i < 0 || i >= WORLD.REGIONS().all().size())
			throw new IllegalArgumentException("no region " + i + " (region list)");
		return WORLD.REGIONS().getByIndex(i);
	}

	private static Faction faction(String s) {
		if (s.equalsIgnoreCase("player"))
			return FACTIONS.player();
		try {
			Faction f = FACTIONS.getByIndex(Integer.parseInt(s));
			if (f.isActive())
				return f;
		} catch (NumberFormatException e) {
			for (Faction x : Lists.of(FACTIONS.active()))
				if (("" + x.name).equalsIgnoreCase(s))
					return x;
		}
		throw new IllegalArgumentException("no active faction '" + s + "' (faction)");
	}

	private ModMilitary() {}
}
