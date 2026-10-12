package lordapcom.cdream;

import world.WORLD;
import world.map.regions.centre.WCentre;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/**
 * Relaxes the capital terrain check: only a 3x3 area fully covered by mountain stays invalid.
 * Applies to the player's own capital only (the placement tool, and the player region once placed),
 * never to the AI region centres chosen by the world generator.
 */
@Mixin(target = "world.map.regions.centre.WorldCentrePlacablity")
public final class CapitalPlacementMixin {

	private static final String[] PLACEMENT_UI = { "view.world.generator.StageCapitol", "world.map.regions.Placer" };

	// game-src world/map/regions/centre/WorldCentrePlacablity.java:30 terrain(int,int)
	@ModifyReturn(method = "terrain", params = { int.class, int.class }, require = true)
	public CharSequence relax(Object self, CharSequence vanilla, int x1, int y1) {
		if (vanilla == null)
			return null;
		try {
			int dim = WCentre.TILE_DIM;
			if (x1 < 1 || y1 < 1 || x1 + dim >= WORLD.TWIDTH() || y1 + dim >= WORLD.THEIGHT())
				return vanilla; // map-edge rule stays
			boolean allMountain = true;
			for (int y = y1; y < y1 + dim && allMountain; y++)
				for (int x = x1; x < x1 + dim; x++)
					if (!WORLD.MOUNTAIN().coversTile(x, y)) {
						allMountain = false;
						break;
					}
			if (allMountain)
				return vanilla;
			if (WORLD.REGIONS().player.is(x1 + dim / 2, y1 + dim / 2) || calledFromPlacementUi())
				return null;
		} catch (Throwable t) {
			Log.once("CapitalPlacementMixin", t);
		}
		return vanilla;
	}

	/**
	 * True only when the first game frame calling the check is the placement tool itself. Looking anywhere up
	 * the stack is wrong: world generation runs below StageCapitol too, and GenAssign.canBeCentre would then seed
	 * AI regions in open water that never get a centre (GenPort crashes on them).
	 */
	private static boolean calledFromPlacementUi() {
		String caller = StackWalker.getInstance().walk(s -> s.map(f -> f.getClassName())
			.filter(n -> !n.startsWith("vizardalpha.") && !n.startsWith("lordapcom.") && !n.startsWith("java.")
				&& !n.startsWith("jdk.") && !n.startsWith("world.map.regions.centre.WorldCentrePlacablity"))
			.findFirst().orElse(""));
		for (String p : PLACEMENT_UI)
			if (caller.startsWith(p))
				return true;
		return false;
	}
}
