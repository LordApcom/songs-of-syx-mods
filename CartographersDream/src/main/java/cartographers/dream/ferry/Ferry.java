package cartographers.dream.ferry;

import java.io.IOException;

import init.constant.C;
import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.spirte.HSprite;
import settlement.entity.humanoid.spirte.HSprites;
import settlement.main.SETT;
import settlement.thing.halfEntity.Factory;
import settlement.thing.halfEntity.HalfEntity;
import snake2d.Renderer;
import snake2d.util.datatypes.DIR;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import util.gui.misc.GBox;
import util.rendering.ShadowBatch;

/**
 * Ferry: a HalfEntity boat that carries one Humanoid host across a list of
 * water tiles between two land tiles. Used for island/sea-bound capitals
 * where immigrants can't reach the throne on foot — they walk to a pickup
 * land tile, embark, ride this ferry through the water, and disembark on
 * the throne-island side.
 *
 * The boat sprite is reused from the vanilla DingyFactory (via SETT.HALFENTS().dingy.renderBoat).
 * The host humanoid is rendered on top using the standard HSprites,
 * driven by the host's race appearance ("indu()").
 *
 * Movement: sub-tile, linear interpolation between consecutive waypoints,
 * one tile per ~SPEED seconds. When the last waypoint is consumed, the
 * host is restored to the disembark land tile and the ferry self-removes.
 *
 * Lifetime invariants:
 *  - host humanoid must be moved-into-the-unknown BEFORE init (caller's
 *    responsibility), so the boat is the only visible entity during transit.
 *  - The waypoint list must be all walkable water tiles (DEEP or BRIDGE).
 *  - If host humanoid is somehow destroyed mid-trip, ferry self-removes.
 */
public final class Ferry extends HalfEntity {

	private static final double SPEED = 2.0;

	// Deferred-cargo identifiers. When a cosmetic ferry arrives and has a
	// non-zero cargoType, it dispatches the matching create call into the
	// owning subsystem (e.g. Caravans.dispatchDeferredCreate). The actor
	// materialises at the drop tile WHEN the boat arrives, not when the
	// caller asked for it — solves the "caravan walks inland before boat
	// even finished sailing" timing bug.
	public static final byte CARGO_NONE = 0;
	public static final byte CARGO_CARAVAN_DELIVER = 1;
	public static final byte CARGO_CARAVAN_FETCH = 2;
	// CARGO_CARAVAN_DELIVER_THRONE was a V70-only fallback. V71 vanilla
	// TypeDeliver internally cascades to findDump (stockpile near throne)
	// when no Import room is available, so the throne-dump cargo type is no
	// longer needed and we just use CARGO_CARAVAN_DELIVER for both flows.
	public static final byte CARGO_CARAVAN_DELIVER_THRONE = 3;

	private int hostId = -1;
	private int wayIdx = 0;
	private double mov = 0;
	private byte dirByte = (byte) DIR.E.id();
	private int disembarkTx = -1;
	private int disembarkTy = -1;

	private byte cargoType = CARGO_NONE;
	private int cargoResIdx = -1;
	private int cargoAmount = 0;
	// V71 Caravan.init requires a TRADE_TYPE; we serialise the TRADE_TYPE.all
	// index so the deferred-cargo dispatch on arrival picks the right one
	// (trade / tax / spoils / diplomacy). 0 (= trade) is a sensible default
	// for legacy ferries loaded from V70 saves.
	private byte cargoTType = 0;

	private int[] wayX = new int[0];
	private int[] wayY = new int[0];

	public Ferry() {
		super(C.TILE_SIZE * 2, C.TILE_SIZE * 2);
	}

	/**
	 * Initialise a freshly-created ferry for a real trip.
	 *
	 * @param host          the humanoid passenger. Caller MUST have already
	 *                      called moveIntoTheTheUnknown on this entity.
	 * @param path          the list of water-tile coordinates (interleaved
	 *                      x,y pairs) from pickup-adjacent water to
	 *                      disembark-adjacent water, inclusive.
	 * @param disembarkTx,disembarkTy
	 *                      the land tile where the host should re-appear on
	 *                      arrival.
	 */
	public boolean init(Humanoid host, int[] pathX, int[] pathY, int disembarkTx, int disembarkTy) {
		if (host == null || pathX == null || pathY == null) return false;
		if (pathX.length < 1 || pathX.length != pathY.length) return false;

		this.hostId = host.id();
		this.wayX = pathX.clone();
		this.wayY = pathY.clone();
		this.wayIdx = 0;
		this.mov = 0;
		this.disembarkTx = disembarkTx;
		this.disembarkTy = disembarkTy;

		body().moveC(wayX[0] * C.TILE_SIZE + C.TILE_SIZEH, wayY[0] * C.TILE_SIZE + C.TILE_SIZEH);

		add();
		return true;
	}

	/**
	 * Initialise an "empty / cosmetic" ferry — no passenger, no disembark
	 * action. Used as a visual indicator that some actor (settlement
	 * caravan, tourist, slave, etc.) crossed the water to reach the
	 * throne side. The boat just sails the path and removes itself at
	 * the end.
	 */
	public boolean initCosmetic(int[] pathX, int[] pathY) {
		return initCosmetic(pathX, pathY, CARGO_NONE, -1, 0, (byte) 0);
	}

	/**
	 * Legacy two-arg cargo overload (no TRADE_TYPE). Defaults to
	 * TRADE_TYPE.trade (index 0) for callers that haven't been ported yet.
	 */
	public boolean initCosmetic(int[] pathX, int[] pathY, byte cargoType, int cargoResIdx, int cargoAmount) {
		return initCosmetic(pathX, pathY, cargoType, cargoResIdx, cargoAmount, (byte) 0);
	}

	/**
	 * Cosmetic ferry with deferred cargo. When the boat reaches the final
	 * waypoint it dispatches the create call for the cargoType subsystem
	 * (currently only Caravans). The actor materialises at the drop tile
	 * AT THAT MOMENT, not at the caller's tick, so the boat-arrives-then-
	 * caravan-walks timing reads naturally.
	 *
	 * cargoTType is the TRADE_TYPE.all index (V71 requirement) — trade=0,
	 * tax=1, spoils=2, diplomacy=3. Set explicitly so the dispatched
	 * Caravan uses the right trade-type accounting.
	 */
	public boolean initCosmetic(int[] pathX, int[] pathY, byte cargoType, int cargoResIdx, int cargoAmount, byte cargoTType) {
		if (pathX == null || pathY == null) return false;
		if (pathX.length < 1 || pathX.length != pathY.length) return false;

		this.hostId = -1;
		this.wayX = pathX.clone();
		this.wayY = pathY.clone();
		this.wayIdx = 0;
		this.mov = 0;
		this.disembarkTx = -1;
		this.disembarkTy = -1;

		this.cargoType = cargoType;
		this.cargoResIdx = cargoResIdx;
		this.cargoAmount = cargoAmount;
		this.cargoTType = cargoTType;

		body().moveC(wayX[0] * C.TILE_SIZE + C.TILE_SIZEH, wayY[0] * C.TILE_SIZE + C.TILE_SIZEH);

		add();
		return true;
	}

	private Humanoid host() {
		if (hostId < 0) return null;
		ENTITY e = SETT.ENTITIES().getByID(hostId);
		if (e instanceof Humanoid) return (Humanoid) e;
		return null;
	}

	@Override
	protected void save(FilePutter file) {
		file.i(hostId);
		file.i(wayIdx);
		file.d(mov);
		file.b(dirByte);
		file.i(disembarkTx);
		file.i(disembarkTy);
		file.b(cargoType);
		file.i(cargoResIdx);
		file.i(cargoAmount);
		file.b(cargoTType);
		file.i(wayX.length);
		for (int i = 0; i < wayX.length; i++) {
			file.i(wayX[i]);
			file.i(wayY[i]);
		}
	}

	@Override
	protected HalfEntity load(FileGetter file) throws IOException {
		hostId = file.i();
		wayIdx = file.i();
		mov = file.d();
		dirByte = file.b();
		disembarkTx = file.i();
		disembarkTy = file.i();
		cargoType = file.b();
		cargoResIdx = file.i();
		cargoAmount = file.i();
		cargoTType = file.b();
		int len = file.i();
		wayX = new int[len];
		wayY = new int[len];
		for (int i = 0; i < len; i++) {
			wayX[i] = file.i();
			wayY[i] = file.i();
		}
		return this;
	}

	@Override
	protected void update(double ds) {
		Humanoid h = host();
		boolean cosmetic = (hostId == -1);

		if (h == null && !cosmetic) {
			// Host gone (slayed, removed, etc). Self-remove.
			remove();
			return;
		}

		if (h != null) {
			// Keep the host in the spatial-grid unknown each tick. Vanilla
			// ENTETIES.update calls Grid.move on every entity, and if the
			// entity's MoveCheck triggers (which it does when the AI
			// manager runs) Grid.move re-adds the host to the grid at its
			// body position — making them visible mid-transit. We undo
			// that here and pin their speed to zero so MoveCheck does not
			// trigger again.
			SETT.ENTITIES().moveIntoTheTheUnknown(h);
			h.speed.magnitudeInit(0);
		}

		if (wayIdx >= wayX.length - 1) {
			if (cosmetic) {
				// Dispatch deferred cargo (caravan/etc.) AT the moment of
				// arrival, so the actor materialises after the boat ride
				// instead of at the original caller's tick. Clear cargoType
				// BEFORE remove() so removeAction() doesn't think this was
				// a premature removal and re-cancel the pre-bump.
				if (cargoType != CARGO_NONE) {
					try {
						settlement.thing.halfEntity.caravan.Caravans.cdDispatchDeferred(
							cargoType, cargoResIdx, cargoAmount, cargoTType);
					} catch (Throwable t) {
						cartographers.dream.CDLog.info("[CartographersDream] Ferry deferred cargo dispatch failed: " + t);
						cartographers.dream.CDLog.err(t);
					}
					cargoType = CARGO_NONE;
				}
				remove();
				return;
			}
			// Reached the last waypoint: disembark and self-remove.
			disembarkAndRemove(h);
			return;
		}

		mov += ds * SPEED;

		int fromX = wayX[wayIdx];
		int fromY = wayY[wayIdx];
		int toX = wayX[wayIdx + 1];
		int toY = wayY[wayIdx + 1];

		double segLen = Math.max(1, Math.abs(toX - fromX) + Math.abs(toY - fromY));
		double t = mov / segLen;

		if (t >= 1.0) {
			mov = 0;
			wayIdx++;
			body().moveC(toX * C.TILE_SIZE + C.TILE_SIZEH, toY * C.TILE_SIZE + C.TILE_SIZEH);
		} else {
			double curX = (fromX + (toX - fromX) * t) * C.TILE_SIZE + C.TILE_SIZEH;
			double curY = (fromY + (toY - fromY) * t) * C.TILE_SIZE + C.TILE_SIZEH;
			body().moveC(curX, curY);
		}

		dirByte = (byte) DIR.get(fromX, fromY, toX, toY).id();
	}

	private void disembarkAndRemove(Humanoid h) {
		// Place host on land disembark tile, restore to spatial grid, self-remove.
		if (SETT.IN_BOUNDS(disembarkTx, disembarkTy)) {
			int px = disembarkTx * C.TILE_SIZE + C.TILE_SIZEH;
			int py = disembarkTy * C.TILE_SIZE + C.TILE_SIZEH;
			h.physics.body().moveC(px, py);
		}
		SETT.ENTITIES().returnFromTheTheUnknown(h);
		hostId = -1;
		remove();
	}

	@Override
	protected void renderBelow(Renderer r, ShadowBatch s, float ds, int x1, int y1) {

		DIR dir = DIR.ALL.get(dirByte & 7);

		int cx = x1 + body().width() / 2;
		int cy = y1 + body().height() / 2;

		// Boat sprite: reuse vanilla Dingy sprite via DingyFactory public wrapper.
		// Pass a stable "random" seed derived from our index so wobble is consistent.
		SETT.HALFENTS().dingy.renderBoat(r, s, cx, cy, dir, index() << 8, 0);

		Humanoid h = host();
		if (h != null) {
			HSprite sp = HSprites.STAND;
			int hx = x1 + body().width() / 2 - 12 * C.SCALE;
			int hy = y1 + body().height() / 2 - 12 * C.SCALE;

			// Rider sits slightly toward the perpendicular direction of travel.
			DIR perp = dir.perpendicular();
			hx += perp.xN() * 2 * C.SCALE;
			hy += perp.yN() * 2 * C.SCALE;

			sp.render(h.indu(), 0, 0, false, perp, null, 0, r, s, ds, hx, hy);
		}
	}

	@Override
	protected void render(Renderer r, ShadowBatch s, float ds, int x, int y) {
		// All rendering happens in renderBelow so the boat sits visually under
		// other entities at the same z; nothing to draw at the base layer.
	}

	@Override
	public int getZ() {
		return 0;
	}

	@Override
	public void hoverInfo(GBox box) {
		// Optional: could show "Immigrant in transit". Leaving blank to match
		// vanilla Dingy which also leaves hoverInfo empty.
	}

	@Override
	protected void removeAction() {
		// Premature removal cleanup. Normal flow (disembarkAndRemove for
		// host-mode, dispatch for cargo-mode) clears hostId / cargoType
		// before calling remove(), so this block only fires if the boat
		// was killed externally — HalfEnts.clear, factory reset, or
		// future bugs that drop the ferry mid-trip.
		//
		// Two leaks we have to plug:
		//   1) Export cargo pre-bumped `tally.attempting` in
		//      Caravans.createFerry. If the boat dies before
		//      cdDispatchDeferred undoes that bump, attempting stays
		//      inflated forever, and vanilla ExportFetcher will never
		//      spawn another fetcher for that resource — exports for
		//      it become silently broken for the rest of the game.
		//   2) Host-mode humanoid was moveIntoTheTheUnknown'd. If the
		//      boat dies mid-trip we have to restore them to the grid
		//      or they're orphaned and counted as alive but invisible.
		if (cargoType == CARGO_CARAVAN_FETCH && cargoResIdx >= 0 && cargoAmount > 0) {
			try {
				settlement.thing.halfEntity.caravan.Caravans.cdCancelExportPreBump(cargoResIdx, cargoAmount);
			} catch (Throwable t) {
				cartographers.dream.CDLog.info("[CartographersDream] Ferry removeAction: cancel pre-bump failed " + t);
			}
		}
		if (hostId != -1) {
			try {
				ENTITY e = SETT.ENTITIES().getByID(hostId);
				if (e != null && !e.isRemoved()) {
					SETT.ENTITIES().returnFromTheTheUnknown(e);
				}
			} catch (Throwable t) {
				cartographers.dream.CDLog.info("[CartographersDream] Ferry removeAction: host restore failed " + t);
			}
		}
	}

	@Override
	protected Factory<? extends HalfEntity> constructor() {
		// FerryFactoryHolder.get() should always be non-null at this point:
		// any Ferry that exists was instantiated via the holder, so the
		// holder is already installed. Worst case (e.g. hot mod reload mid-
		// save) it returns null and save throws — better than corrupting
		// the save with the wrong factory index.
		return FerryFactoryHolder.get();
	}
}
