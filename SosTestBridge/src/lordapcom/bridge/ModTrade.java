package lordapcom.bridge;

import java.util.ArrayList;
import java.util.List;

import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.diplomacy.deal.Deal;
import game.faction.diplomacy.deal.DealParty;
import game.faction.npc.FactionNPC;
import game.faction.player.PTrade;
import init.trade.TR;
import init.trade.TRADABLE;
import settlement.trade.PBuyer;
import settlement.trade.PSeller;

/** World trade: import/export settings as the player's goods UI sets them, plus manual buy/sell deals. */
final class ModTrade {

	static void register() {
		Registry.add("trade", "trade list | trade export <key> <limit 0-100|off> [priceCap] | trade import <key> <limit 0-100|off> [priceCap] [minMoney] | trade buy|sell <factionIdx> <key> <n> | trade deliver <resource> <n>  (key = RES_xxx / SLAVE_xxx or bare resource key)", ModTrade::trade);
	}

	private static final String USAGE = "trade list | trade export <key> <limit 0-100|off> [priceCap] | trade import <key> <limit 0-100|off> [priceCap] [minMoney] | trade buy|sell <factionIdx> <key> <n>";

	private static Object trade(String[] a) {
		if (a.length < 2 || a[1].equals("list"))
			return list();
		switch (a[1]) {
		case "export": return export(a);
		case "import": return imp(a);
		case "buy": return deal(a, true);
		case "sell": return deal(a, false);
		case "deliver": { // a merchant caravan arrives with goods, as when an import lands (Caravans.createDelivery)
			init.resources.RESOURCE r = null;
			for (init.resources.RESOURCE x : Lists.of(init.resources.RESOURCES.ALL()))
				if (x.key.equalsIgnoreCase(a[2]))
					r = x;
			if (r == null)
				throw new IllegalArgumentException("unknown resource " + a[2]);
			return Commands.m("created", settlement.main.SETT.HALFENTS().caravans.createDelivery(r, Commands.i(a, 3), init.trade.TRADE_TYPE.trade));
		}
		default: throw new IllegalArgumentException(USAGE);
		}
	}

	private static TRADABLE tradable(String k) {
		for (TRADABLE t : Lists.of(TR.ALL()))
			if (t.key().equalsIgnoreCase(k) || t.key().equalsIgnoreCase("RES_" + k) || t.key().equalsIgnoreCase("RES" + k)
					|| t.key().equalsIgnoreCase("SLAVE_" + k)) // "RES" + "_WOOD": resource keys from res list
				return t;
		throw new IllegalArgumentException("unknown tradable " + k + " (trade list)");
	}

	private static int limit(String s) {
		return s.equals("off") ? 0 : Integer.parseInt(s);
	}

	// game path: view/ui/goods/UIGoodsExport.java:329-346 amount() slider -> FACTIONS.player().seller(res).limit.set; price cap :280-298 -> priceCapsI.set
	private static Object export(String[] a) {
		if (a.length < 4)
			throw new IllegalArgumentException("trade export <key> <limit 0-100|off> [priceCap]");
		TRADABLE t = tradable(a[2]);
		PSeller s = FACTIONS.player().seller(t);
		s.limit.set(limit(a[3]));
		if (a.length > 4)
			s.priceCapsI.set(Commands.i(a, 4));
		return Commands.m("key", t.key(), "exportLimit", s.limit.get(), "priceCap", s.priceCapsI.get());
	}

	// game path: view/ui/goods/UIGoodsImport.java:375-395 amount() slider -> buyer(res).limit.set; minMoney :340-360 -> minMoney.set; price cap -> priceCapsI.set
	private static Object imp(String[] a) {
		if (a.length < 4)
			throw new IllegalArgumentException("trade import <key> <limit 0-100|off> [priceCap] [minMoney]");
		TRADABLE t = tradable(a[2]);
		PBuyer b = FACTIONS.player().buyer(t);
		b.limit.set(limit(a[3]));
		if (a.length > 4)
			b.priceCapsI.set(Commands.i(a, 4));
		if (a.length > 5)
			b.minMoney.set(Commands.i(a, 5));
		return Commands.m("key", t.key(), "importLimit", b.limit.get(), "priceCap", b.priceCapsI.get(), "minMoney", b.minMoney.get());
	}

	// game path: game/faction/player/PTrade.java (prices, units/credits traded histories); settings as in export/import above
	private static Object list() {
		PTrade p = FACTIONS.player().trade;
		List<Object> l = new ArrayList<>();
		for (TRADABLE t : Lists.of(TR.ALL())) {
			PSeller s = FACTIONS.player().seller(t);
			PBuyer b = FACTIONS.player().buyer(t);
			l.add(Commands.m("key", t.key(), "name", t.name.toString(),
					"exportLimit", s.limit.get(), "exportPriceCap", s.priceCapsI.get(),
					"importLimit", b.limit.get(), "importPriceCap", b.priceCapsI.get(), "importMinMoney", b.minMoney.get(),
					"sellPrice", p.pricesSell.get(t), "buyPrice", p.pricesBuy.get(t),
					"unitsExported", p.unitsExported.get(t), "unitsImported", p.unitsImported.get(t),
					"earned", p.inExported.get(t), "spent", p.outImported.get(t)));
		}
		return l;
	}

	// game path: view/world/ui/faction/UIFactions.java:84 openBuy / :93 openSell (deal setup) + view/world/ui/faction/UIDiplomacy.java:101 deal.execute(true) (Accept button)
	private static Object deal(String[] a, boolean buy) {
		if (a.length < 5)
			throw new IllegalArgumentException("trade " + a[1] + " <factionIdx> <key> <n>");
		Faction f = FACTIONS.getByIndex(Commands.i(a, 2));
		if (!(f instanceof FactionNPC) || !f.isActive())
			throw new IllegalArgumentException("not an active kingdom: " + a[2]);
		TRADABLE t = tradable(a[3]);
		int n = Commands.i(a, 4);
		Deal d = new Deal();
		d.setFactionAndClear((FactionNPC) f, true);
		// the UI's own slider ceilings: what the seller has / what the buyer will take
		DealParty give = buy ? d.npc : d.player;
		DealParty pay = buy ? d.player : d.npc;
		int max = give.resources.max(t);
		give.resources.set(t, Math.min(n, max));
		int got = give.resources.get(t);
		// balance with credits like the player does with the credit input
		pay.credits.set((int) Math.ceil(give.value() - pay.value()));
		if (!d.canBeAccepted())
			throw new IllegalArgumentException("deal refused by the game (max " + max + ", value " + (int) d.valueCredits()
					+ ", credits available " + pay.credits.max() + ")");
		int credits = pay.credits.get();
		d.execute(true);
		return Commands.m("faction", f.index(), "key", t.key(), buy ? "bought" : "sold", got, "credits", credits);
	}

	private ModTrade() {}
}
