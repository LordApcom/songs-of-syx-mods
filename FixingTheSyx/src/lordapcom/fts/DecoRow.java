package lordapcom.fts;

import init.constant.C;
import settlement.main.SETT;
import settlement.room.infra.monument.ROOM_MONUMENT;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.placement.RoomPlacer;
import snake2d.MButt;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.color.OPACITY;
import snake2d.util.datatypes.DIR;
import snake2d.util.misc.CLAMP;
import util.colors.GCOLOR;
import view.keyboard.KEYS;
import view.main.VIEW;
import view.subview.GameWindow;
import view.tool.PLACABLE;
import view.tool.PlacableFixed;
import vizardalpha.polyphon.api.Shadow;

/** Feature 2: decoration row placer. State is per game session, nothing is saved. */
final class DecoRow {

	private static final int[] DX = {1, 0, -1, 0};
	private static final int[] DY = {0, 1, 0, -1};

	private static int count = 1;
	private static int spacing = -1; // -1 = derive from the item's effect radius
	private static int dir = 0; // E, S, W, N
	private static Object lastKey;

	private DecoRow() {
	}

	/** The placer, if it is the item placer of a Decorations-category room; else null. */
	static PlacableFixed deco(Object p) {
		if (!(p instanceof PlacableFixed))
			return null;
		RoomPlacer rp = SETT.ROOMS().placement.placer;
		if (p != rp.itemPlacerCurrent())
			return null;
		RoomBlueprintImp b = rp.blueprint();
		if (b == null || b.cat != SETT.ROOMS().CATS.DECOR)
			return null;
		return (PlacableFixed) p;
	}

	private static int defaultSpacing(PlacableFixed p) {
		RoomBlueprintImp b = SETT.ROOMS().placement.placer.blueprint();
		if (b instanceof ROOM_MONUMENT) {
			ROOM_MONUMENT m = (ROOM_MONUMENT) b;
			// same lookup as ROOM_MONUMENT.radius(FurnisherItem), which indexes by item width
			int r = m.radius[CLAMP.i(p.width() - 1, 0, m.radius.length - 1)];
			return 2 * r - 1; // zones overlap by exactly one tile
		}
		return Math.max(p.width(), p.height());
	}

	/** Centre-to-centre step in tiles along the row direction. */
	static int step(PlacableFixed p) {
		Object key = SETT.ROOMS().placement.placer.blueprint();
		int sig = p.width() * 31 + p.height();
		if (key != lastKey || sig != lastSig) {
			lastKey = key;
			lastSig = sig;
			spacing = -1;
		}
		int min = dir % 2 == 0 ? p.width() : p.height();
		return Math.max(min, spacing > 0 ? spacing : defaultSpacing(p));
	}

	private static int lastSig;

	/** Wheel and R handling. Runs before the vanilla tool so it can swallow the wheel. */
	static void input(Object toolPlacer) {
		PLACABLE orig = Shadow.get(toolPlacer, "origional");
		PlacableFixed p = deco(orig);
		if (p == null || !VIEW.s().tools.isHovered())
			return;
		if (KEYS.MAIN().ROTATE.consumeClick())
			dir = (dir + 1) & 3;
		float w = MButt.peekWheel();
		if (w == 0)
			return;
		int s = w > 0 ? 1 : -1;
		if (Keys.ctrl() && Keys.alt()) {
			count = CLAMP.i(count + s, 1, 64);
			MButt.clearWheelSpin();
		} else if (Keys.ctrl() && Keys.shift()) {
			spacing = CLAMP.i(step(p) + s, 1, 64);
			MButt.clearWheelSpin();
		}
	}

	/** Places the whole row. Returns false to let vanilla place a single copy. */
	static boolean place(Object tool, GameWindow window) {
		PlacableFixed p = deco(Shadow.get(tool, "placable"));
		if (p == null || count <= 1)
			return false;
		int st = step(p);
		int w = p.width(), h = p.height();
		int x1 = window.tile().x() - w / 2, y1 = window.tile().y() - h / 2;
		p.init(window.tile().x(), window.tile().y());
		for (int k = 0; k < count; k++) {
			int ox = x1 + DX[dir] * st * k, oy = y1 + DY[dir] * st * k;
			if (fits(p, ox, oy)) {
				for (int dy = 0; dy < h; dy++)
					for (int dx = 0; dx < w; dx++)
						p.place(ox + dx, oy + dy, dx, dy);
				p.afterPlaced(ox, oy);
			}
		}
		return true;
	}

	private static boolean fits(PlacableFixed p, int x1, int y1) {
		if (p.placableWhole(x1, y1) != null)
			return false;
		for (int dy = 0; dy < p.height(); dy++)
			for (int dx = 0; dx < p.width(); dx++)
				if (p.placable(x1 + dx, y1 + dy, dx, dy) != null)
					return false;
		return true;
	}

	/** Translucent ghosts for every copy after the first (the vanilla render draws the first). */
	static void ghosts(Object tool, SPRITE_RENDERER r, GameWindow window) {
		PlacableFixed p = deco(Shadow.get(tool, "placable"));
		if (p == null || count <= 1)
			return;
		int st = step(p);
		int w = p.width(), h = p.height();
		int x1 = window.tile().x() - w / 2, y1 = window.tile().y() - h / 2;
		for (int k = 1; k < count; k++) {
			int ox = DX[dir] * st * k, oy = DY[dir] * st * k;
			boolean whole = p.placableWhole(x1 + ox, y1 + oy) == null;
			for (int dy = 0; dy < h; dy++) {
				for (int dx = 0; dx < w; dx++) {
					boolean ok = p.placable(x1 + ox + dx, y1 + oy + dy, dx, dy) == null;
					(ok ? (whole ? GCOLOR.MAP().OK : GCOLOR.MAP().SOSO) : GCOLOR.MAP().BAD).bind();
					OPACITY.O50.bind();
					int x = window.tile().rel().x() + (-w / 2 + dx + ox) * C.TILE_SIZE;
					int y = window.tile().rel().y() + (-h / 2 + dy + oy) * C.TILE_SIZE;
					int m = 0;
					if (dx == 0)
						m |= DIR.W.mask();
					if (dx == w - 1)
						m |= DIR.E.mask();
					if (dy == 0)
						m |= DIR.N.mask();
					if (dy == h - 1)
						m |= DIR.S.mask();
					p.renderPlaceHolder(r, ~m & 0x0F, x, y, x1 + ox + dx, y1 + oy + dy, dx, dy, ok, whole);
				}
			}
		}
		OPACITY.unbind();
		COLOR.unbind();
		p.placableWhole(x1, y1); // restore the placer's "current item area" to the cursor copy
		VIEW.hoverBox().NL();
		VIEW.hoverBox().add(VIEW.hoverBox().text().add("Row x" + count + ", step " + st));
	}
}
