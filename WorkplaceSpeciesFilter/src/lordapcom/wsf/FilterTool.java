package lordapcom.wsf;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import init.constant.C;
import init.sprite.SPRITES;
import init.type.HCLASSES;
import init.type.HGROUP;
import settlement.main.ON_TOP_RENDERABLE;
import settlement.main.SETT;
import settlement.room.main.RoomInstance;
import settlement.room.service.module.ROOM_SERVICER;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.datatypes.AREA;
import snake2d.util.color.COLOR;
import snake2d.util.color.ColorImp;
import snake2d.util.color.OPACITY;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GuiSection;
import snake2d.util.gui.Hoverable.HOVERABLE;
import snake2d.util.gui.clickable.CLICKABLE;
import snake2d.util.sets.ArrayList;
import snake2d.util.sets.LIST;
import snake2d.util.sprite.SPRITE;
import util.gui.misc.GBox;
import util.gui.misc.GButt;
import util.gui.misc.GStat;
import util.gui.misc.GText;
import util.rendering.RenderData;
import util.rendering.RenderData.RenderIterator;
import util.rendering.ShadowBatch;
import view.tool.PLACER_TYPE;
import view.tool.PlacableMulti;

/** Click/drag tool: applies the picked species/class selection as the worker or user filter of buildings. */
final class FilterTool extends PlacableMulti {

	static final String TITLE = "Species filter (workplaces / services)";
	static final String DESC = "Choose which species and classes may WORK in a building (Workers mode) or USE its service (Users mode), then click or drag over buildings. While this tool is open every workplace and service building shows its filter: ? = everyone.";

	private static final int U = C.TILE_SIZE;

	private boolean workers = true;
	private final boolean[][] sel = new boolean[2][HGROUP.all().size()]; // [0] workers, [1] users
	private final LIST<CLICKABLE> butts;

	FilterTool() {
		super(TITLE, DESC, SPRITES.icons().m.citizen);
		Arrays.fill(sel[0], true);
		Arrays.fill(sel[1], true);

		GuiSection root = new GuiSection();

		GStat stat = new GStat() {
			@Override
			public void update(GText t) {
				if (workers)
					t.warnify().add("SETTING: WORKERS (who may work here)");
				else
					t.selectify().add("SETTING: USERS (who may use the service)");
			}
		}.increase();
		HOVERABLE header = stat.r(DIR.C);
		root.add(header);

		GuiSection modes = new GuiSection();
		modes.addRightC(0, mode("Workers", "Filter who may take a job in the building.", true));
		modes.addRightC(8, mode("Users", "Filter who may use the service of the building (baths, temples, stages, canteens...).", false));
		root.addRelBody(4, DIR.S, modes);

		GuiSection quick = new GuiSection();
		quick.addRightC(0, quick(SPRITES.icons().m.questionmark, "Everyone", null));
		quick.addRightC(0, quick(HCLASSES.CITIZEN().icon(), "All " + HCLASSES.CITIZEN().names, HCLASSES.CITIZEN()));
		quick.addRightC(0, quick(HCLASSES.SLAVE().icon(), "All " + HCLASSES.SLAVE().names, HCLASSES.SLAVE()));
		quick.addRightC(0, quick(SPRITES.icons().m.cancel, "Nobody", HCLASSES.NOBLE()));
		root.addRelBody(8, DIR.S, quick);

		GuiSection grid = new GuiSection();
		for (HGROUP t : Lists.of(HGROUP.all())) {
			CLICKABLE bb = new Tog(t.icon, true) {
				@Override
				protected void clickA() {
					boolean[] s = cur();
					s[t.index] = !s[t.index];
				}

				@Override
				boolean on() {
					return cur()[t.index];
				}
			}.hoverInfoSet("Allow (click to toggle): " + t.name);
			if (grid.getLastX2() > 500) {
				bb.body().moveX1(0).moveY1(grid.body().y2());
				grid.add(bb);
			} else
				grid.addRightC(0, bb);
		}
		root.addRelBody(8, DIR.S, grid);

		butts = new ArrayList<CLICKABLE>(root);
	}

	private boolean[] cur() {
		return sel[workers ? 0 : 1];
	}

	private CLICKABLE mode(String label, String hover, boolean w) {
		return new GButt.ButtPanel(label) {
			@Override
			protected void clickA() {
				workers = w;
			}

			@Override
			protected void renAction() {
				selectedSet(workers == w);
			}
		}.hoverInfoSet(hover);
	}

	/** cls == null: everyone; cls == NOBLE: nobody (used as the "none" marker); otherwise that class only. */
	private CLICKABLE quick(SPRITE icon, String hover, init.type.HCLASS cls) {
		return new Tog(icon, false) {
			@Override
			protected void clickA() {
				boolean[] s = cur();
				for (HGROUP g : Lists.of(HGROUP.all()))
					s[g.index] = cls == null || g.type == cls;
			}

			@Override
			boolean on() { // active when the current selection is exactly this preset
				boolean[] s = cur();
				for (HGROUP g : Lists.of(HGROUP.all()))
					if (s[g.index] != (cls == null || g.type == cls))
						return false;
				return true;
			}
		}.hoverInfoSet(hover);
	}

	/** Button with an unmistakable state: on = green frame; off = darkened, with a red cross when crossOff. */
	private abstract static class Tog extends GButt.ButtPanel {
		private final boolean crossOff;

		Tog(SPRITE icon, boolean crossOff) {
			super(icon);
			this.crossOff = crossOff;
		}

		abstract boolean on();

		@Override
		protected void render(SPRITE_RENDERER r, float ds, boolean isActive, boolean isSelected, boolean isHovered) {
			super.render(r, ds, isActive, false, isHovered);
			int x1 = body().x1(), x2 = body().x2(), y1 = body().y1(), y2 = body().y2();
			if (on()) {
				COLOR.GREEN100.render(r, x1, x2, y1, y1 + 3);
				COLOR.GREEN100.render(r, x1, x2, y2 - 3, y2);
				COLOR.GREEN100.render(r, x1, x1 + 3, y1, y2);
				COLOR.GREEN100.render(r, x2 - 3, x2, y1, y2);
			} else {
				OPACITY.O66.bind();
				COLOR.BLACK.render(r, x1, x2, y1, y2);
				OPACITY.unbind();
				if (crossOff) {
					COLOR.RED100.bind();
					SPRITES.icons().m.anti.render(r, x1 + 2, x2 - 2, y1 + 2, y2 - 2);
					COLOR.unbind();
				}
			}
		}
	}

	@Override
	public CharSequence isPlacable(int tx, int ty, AREA area, PLACER_TYPE type) {
		RoomInstance ins = SETT.ROOMS().map.instance.get(tx, ty);
		if (ins == null)
			return "Point at a building.";
		if (workers && ins.blueprintI().employment() == null)
			return "This building has no workers.";
		if (!workers && !(ins instanceof ROOM_SERVICER))
			return "This building offers no service.";
		return null;
	}

	@Override
	public void place(int tx, int ty, AREA area, PLACER_TYPE type) {
		RoomInstance ins = SETT.ROOMS().map.instance.get(tx, ty);
		if (ins != null)
			Filters.set(ins, workers, Filters.normalize(cur()));
	}

	@Override
	public void placeInfo(GBox b, int oktiles, AREA a) {
		super.placeInfo(b, oktiles, a);
		b.NL();
		GText t = b.text();
		if (workers)
			t.warnify().add("Setting WORKERS filter");
		else
			t.selectify().add("Setting USERS filter");
		b.add(t);
	}

	@Override
	public LIST<CLICKABLE> getAdditionalButt() {
		ren.add();
		return butts;
	}

	// Per-frame scratch for the overlay.
	private final IdentityHashMap<RoomInstance, int[]> best = new IdentityHashMap<>();
	private final java.util.ArrayList<HGROUP> shown = new java.util.ArrayList<>();
	private SPRITE single;
	private boolean anti;
	private int iw, ih, cw, ch, cols;

	/** Outlines every workplace area, then marks it (hammer) and service building (heart) with its filter; the current mode is drawn bright. */
	private final ON_TOP_RENDERABLE ren = new ON_TOP_RENDERABLE() {

		@Override
		public void render(Renderer r, ShadowBatch shadowBatch, RenderData data, double ds) {
			try {
				best.clear();
				// Pick, for each building, its on-screen tile closest to the building centre.
				RenderIterator it = data.onScreenTiles();
				while (it.has()) {
					RoomInstance ins = SETT.ROOMS().map.instance.get(it.tx(), it.ty());
					if (ins != null && (ins.blueprintI().employment() != null || ins instanceof ROOM_SERVICER)) {
						area(r, ins, it.tx(), it.ty(), it.x(), it.y());
						int d = Math.abs(it.tx() - ins.body().cX()) + Math.abs(it.ty() - ins.body().cY());
						int[] b = best.get(ins);
						if (b == null)
							best.put(ins, new int[] { d, it.x(), it.y() });
						else if (d < b[0]) {
							b[0] = d;
							b[1] = it.x();
							b[2] = it.y();
						}
					}
					it.next();
				}
				for (Map.Entry<RoomInstance, int[]> e : best.entrySet()) {
					RoomInstance ins = e.getKey();
					int cx = e.getValue()[1] + C.TILE_SIZEH; // it.x()/y() are the tile's top-left corner
					int cy = e.getValue()[2] + C.TILE_SIZEH;
					Filters.Entry f = Filters.get(ins);
					boolean w = ins.blueprintI().employment() != null;
					boolean u = ins instanceof ROOM_SERVICER;
					int[] wb = f == null ? null : f.w;
					int[] ub = f == null ? null : f.u;
					int hw = w ? height(wb) : 0;
					int hu = u ? height(ub) : 0;
					int gap = w && u ? U / 8 : 0;
					int y = cy - (hw + gap + hu) / 2;
					if (w)
						y += badge(r, cx, y, true, wb);
					if (u) {
						y += gap;
						badge(r, cx, y, false, ub);
					}
				}
			} catch (Throwable t) {
				Filters.err("overlay", t);
			}
			remove();
		}
	};

	private static final COLOR TINT_W = new ColorImp(127, 80, 0);
	private static final COLOR TINT_U = new ColorImp(30, 90, 127);

	/** Outlines a building on the tile edges that face outside it; the ground texture stays visible (no fill). */
	private void area(Renderer r, RoomInstance ins, int tx, int ty, int x, int y) {
		boolean w = ins.blueprintI().employment() != null;
		boolean on = workers ? w : ins instanceof ROOM_SERVICER;
		COLOR c = on == workers ? TINT_W : TINT_U; // current mode's colour when it applies, else the other mode's
		// ponytail: no tile fill - the filled sprite renders as the void texture in the on-top pass and hides the field.
		int mask = 0;
		for (int i = 0; i < DIR.ORTHO.size(); i++) {
			DIR d = DIR.ORTHO.get(i);
			if (ins.is(tx + d.x(), ty + d.y()))
				mask |= d.mask(); // set = neighbour inside (no border on that side), as the game's area outlines
		}
		if (mask == 15)
			return;
		(on ? OPACITY.O100 : OPACITY.O50).bind();
		c.bind();
		SPRITES.cons().BIG.outline.render(r, mask, x, y);
		COLOR.unbind();
		OPACITY.unbind();
	}

	/** Fills the scratch fields with the compact content for a filter. */
	private void measure(int[] bits) {
		single = null;
		anti = false;
		shown.clear();
		int n = 0, cit = 0, slv = 0, citT = 0, slvT = 0;
		List<HGROUP> all = Lists.of(HGROUP.all());
		for (HGROUP g : all) {
			boolean ok = Filters.allows(bits, g);
			boolean c = g.type == HCLASSES.CITIZEN();
			if (c)
				citT++;
			else
				slvT++;
			if (ok) {
				n++;
				if (c)
					cit++;
				else
					slv++;
			}
		}
		if (bits == null)
			single = SPRITES.icons().m.questionmark;
		else if (n == 0)
			single = SPRITES.icons().m.cancel;
		else if (cit == citT && slv == 0)
			single = HCLASSES.CITIZEN().icon();
		else if (slv == slvT && cit == 0)
			single = HCLASSES.SLAVE().icon();
		if (single != null) {
			ih = U * 3 / 4;
			iw = ih;
			cw = iw;
			ch = ih;
			return;
		}
		// Mixed: list the allowed groups, or the excluded ones (crossed out) when those are fewer.
		anti = n > all.size() / 2;
		for (HGROUP g : all)
			if (Filters.allows(bits, g) != anti)
				shown.add(g);
		ih = U / 2;
		iw = ih * 3 / 2;
		cols = Math.min(shown.size(), 5);
		cw = cols * iw;
		ch = ((shown.size() + cols - 1) / cols) * ih;
	}

	private int height(int[] bits) {
		measure(bits);
		return Math.max(ch, U / 2) + U / 4;
	}

	/** Draws one badge centred on cx with its top at y1; returns its height. */
	private int badge(Renderer r, int cx, int y1, boolean worker, int[] bits) {
		measure(bits);
		int p = U / 8, k = U / 2;
		int h = Math.max(ch, k) + 2 * p;
		int w = p + k + p + cw + p;
		int x1 = cx - w / 2;
		boolean on = worker == workers;
		// ponytail: icons only, like the game's work-group overlay (UIRoomRaceAssign): a COLOR fill in the on-top pass
		// renders as the void texture, so a background box came out as a black square hiding the icons.
		(on ? OPACITY.O100 : OPACITY.O50).bind();
		(worker ? COLOR.ORANGE100 : COLOR.BLUE100).bind();
		(worker ? SPRITES.icons().s.hammer : SPRITES.icons().s.heart).render(r, x1 + p, x1 + p + k, y1 + (h - k) / 2, y1 + (h - k) / 2 + k);
		COLOR.unbind();
		int ix = x1 + p + k + p;
		if (single != null) {
			single.render(r, ix, ix + iw, y1 + (h - ih) / 2, y1 + (h - ih) / 2 + ih);
		} else {
			int iy = y1 + (h - ch) / 2;
			for (int i = 0; i < shown.size(); i++) {
				int gx = ix + (i % cols) * iw;
				int gy = iy + (i / cols) * ih;
				shown.get(i).icon.render(r, gx, gx + iw, gy, gy + ih);
				if (anti)
					SPRITES.icons().m.anti.render(r, gx + (iw - ih) / 2, gx + (iw - ih) / 2 + ih, gy, gy + ih);
			}
		}
		OPACITY.unbind();
		return h;
	}
}
