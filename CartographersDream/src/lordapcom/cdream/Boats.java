package lordapcom.cdream;

import java.util.ArrayList;
import java.util.IdentityHashMap;

import init.constant.C;
import settlement.entity.ENTITY;
import settlement.entity.humanoid.Humanoid;
import settlement.entity.humanoid.spirte.HSprites;
import settlement.main.SETT;
import settlement.thing.halfEntity.caravan.Caravan;
import snake2d.Renderer;
import snake2d.util.datatypes.DIR;
import snake2d.util.datatypes.RECTANGLE;
import util.rendering.ShadowBatch;

/**
 * Arrivals at a sea-locked capital come by boat: the game's fishing boat sails from the map edge to the shore with
 * the newcomer standing in it (drawn with their own species), and the newcomer only appears on land when it lands.
 * Same for merchant caravans (the boat carries their goods) and for anyone spawned out on the sea (invaders), who
 * are rowed to the nearest dock. While aboard, the passenger's own update is skipped and it is off the entity grid.
 *
 * ponytail: trips are not saved. A save taken mid-trip reloads the passenger where it was put (shore, or the sea
 * tile for an invader); add a saved trip list if that shows up in play.
 */
final class Boats {

	private static final double SPEED = 4.0; // tiles per second (the game's fishing boat does 2)
	private static final int SEARCH = 8;
	/** ponytail: the trip starts at most this many tiles out, so nobody waits minutes crossing a big sea map. */
	private static final int MAX_TRIP = 80;

	private static final class Trip {
		int[] path; // tile indices, sea, start first
		double pos;
		Humanoid man;
		Caravan cargo;
		int landX = -1, landY = -1; // where the passenger is put on arrival; -1 = stays where it spawned
		int lane; // sideways offset in pixels: boats leaving together sail four abreast
	}

	private static final ArrayList<Trip> trips = new ArrayList<>();
	private static final IdentityHashMap<Object, Trip> aboard = new IdentityHashMap<>();
	/** Invader divisions whose formation was already moved to the shore they landed on. */
	private static final IdentityHashMap<Object, Boolean> regrouped = new IdentityHashMap<>();

	/** Set when the game hands out a dock as an arrival spot; the next newcomer near it is an arrival. */
	private static int armedDock = -1;
	private static int spread;

	private Boats() {
	}

	static void reset() {
		trips.clear();
		aboard.clear();
		regrouped.clear();
		armedDock = -1;
	}

	static void arm(int dockX, int dockY) {
		armedDock = dockX + dockY * SETT.TWIDTH;
	}

	static boolean aboard(Object o) {
		return aboard.containsKey(o);
	}

	/** A humanoid was just added to the world. */
	static void added(ENTITY e) {
		if (!Island.locked || !(e instanceof Humanoid))
			return;
		Humanoid h = (Humanoid) e;
		int tx = h.physics.tileC().x(), ty = h.physics.tileC().y();
		Trip t = new Trip();
		if (!SETT.PATH().connectivity.is(tx, ty)) {
			// spawned where the capital can't be walked to (invaders use the map edge: sea, or land across the sea)
			// They land spread over the half of the shore farthest from the throne: the game counts an invader standing
			// next to the throne as a lost battle, and on a small island the nearest dock can be a few steps from it.
			int[] far = Island.farDocks(settlement.room.main.throne.THRONE.coo().x(), settlement.room.main.throne.THRONE.coo().y());
			if (far.length == 0)
				return;
			int dock = far[(spread++ & 0x7fffffff) % far.length];
			t.path = Island.routeFromEdge(dock % SETT.TWIDTH, dock / SETT.TWIDTH, SEARCH);
			if (t.path == null)
				return;
			t.landX = dock % SETT.TWIDTH;
			t.landY = dock / SETT.TWIDTH;
		} else {
			if (!consumeArm(tx, ty))
				return;
			t.path = Island.routeFromEdge(tx, ty, SEARCH);
			if (t.path == null)
				return;
		}
		t.man = h;
		start(t, h);
		SETT.ENTITIES().moveIntoTheTheUnknown(h);
	}

	/** A merchant caravan was just created at the shore. */
	static void caravan(Caravan c) {
		if (!Island.locked)
			return;
		int tx = c.body().cX() / C.TILE_SIZE, ty = c.body().cY() / C.TILE_SIZE;
		if (!consumeArm(tx, ty))
			return;
		Trip t = new Trip();
		t.path = Island.routeFromEdge(tx, ty, SEARCH);
		if (t.path == null)
			return;
		t.cargo = c;
		start(t, c);
	}

	private static boolean consumeArm(int tx, int ty) {
		if (armedDock < 0)
			return false;
		int dx = armedDock % SETT.TWIDTH - tx, dy = armedDock / SETT.TWIDTH - ty;
		if (Math.abs(dx) > SEARCH || Math.abs(dy) > SEARCH)
			return false;
		armedDock = -1;
		return true;
	}

	private static void start(Trip t, Object key) {
		if (t.path.length > MAX_TRIP)
			t.path = java.util.Arrays.copyOfRange(t.path, t.path.length - MAX_TRIP, t.path.length);
		// boats leaving from the same spot set out in rows of four, a few tiles apart, so a big landing isn't one long queue
		int same = 0;
		for (Trip o : trips)
			if (o.path[0] == t.path[0] && o.pos < 3)
				same++;
		t.pos = -(same / 4) * 3;
		t.lane = ((same % 4) * 2 - 3) * C.TILE_SIZE * 2 / 3;
		trips.add(t);
		aboard.put(key, t);
	}

	static void update(double ds) {
		if (trips.isEmpty())
			regrouped.clear();
		for (int i = trips.size() - 1; i >= 0; i--) {
			Trip t = trips.get(i);
			if ((t.man != null && t.man.isRemoved()) || (t.cargo != null && !t.cargo.added())) {
				drop(i, t);
				continue;
			}
			t.pos += ds * SPEED;
			if (t.pos >= t.path.length - 1)
				land(i, t);
		}
	}

	private static void land(int i, Trip t) {
		if (t.man != null) {
			if (t.landX >= 0) {
				t.man.physics.body().moveC(t.landX * C.TILE_SIZE + C.TILE_SIZEH, t.landY * C.TILE_SIZE + C.TILE_SIZEH);
				// A soldier's position is only reported to his division when he walks (Humanoid.update), so the jump
				// ashore went unseen: the division stayed "at sea", the war AI kept aiming at targets by the map edge and
				// the men stood on the beach for good. Same call the game makes after moving one (teleportAndInitInDiv).
				if (t.man.division() != null)
					t.man.division().reporter.reportPosition(t.man.divSpot(), t.man.body().cX(), t.man.body().cY());
				regroup(t.man, t.landX, t.landY);
			}
			SETT.ENTITIES().returnFromTheTheUnknown(t.man);
		}
		drop(i, t);
	}

	/**
	 * Invaders are deployed in formation out at the map edge, which is sea here; once rowed ashore they would wade back
	 * toward it and stand in a line in the shallows. The first soldier of a division to land moves the division's formation
	 * to the island, halfway between the dock and the throne, the same call the game's deployer uses (DivDeployer.place).
	 */
	private static void regroup(Humanoid h, int dx, int dy) {
		game.battle.div.Div div = h.division();
		if (div == null || regrouped.put(div, Boolean.TRUE) != null)
			return;
		snake2d.util.datatypes.COORDINATE th = settlement.room.main.throne.THRONE.coo();
		DIR in = DIR.get(dx, dy, th.x(), th.y());
		if (!in.isOrtho())
			in = in.next(1);
		DIR along = in.next(2);
		// a short front halfway between the beach and the throne: on a small island a wide line would stand in the sea
		int w = Math.max(1, Math.min(6, (int) Math.ceil(div.menNrOf() / 5.0)));
		int mx = (dx + th.x()) / 2, my = (dy + th.y()) / 2;
		int cx = mx - along.x() * w / 2, cy = my - along.y() * w / 2;
		int x1 = cx * C.TILE_SIZE + C.TILE_SIZEH, y1 = cy * C.TILE_SIZE + C.TILE_SIZEH;
		game.GAME.ARMIES().placer.deploy(div, x1, x1 + along.x() * w * C.TILE_SIZE, y1, y1 + along.y() * w * C.TILE_SIZE);
	}

	private static void drop(int i, Trip t) {
		trips.remove(i);
		aboard.remove(t.man != null ? t.man : t.cargo);
	}

	/** Called before the game's own boats/caravans are drawn (HalfEnts.renderBelow). */
	static void render(Renderer r, ShadowBatch s, float ds, RECTANGLE win, int offX, int offY) {
		if (trips.isEmpty())
			return;
		final int w = SETT.TWIDTH;
		for (Trip t : trips) {
			if (t.pos < 0)
				continue; // still waiting to set out
			int seg = Math.min((int) t.pos, t.path.length - 2);
			if (seg < 0)
				seg = 0;
			int a = t.path[seg], b = t.path[Math.min(seg + 1, t.path.length - 1)];
			double f = Math.min(1, t.pos - seg);
			int ax = a % w, ay = a / w, bx = b % w, by = b / w;
			int px = (int) ((ax + (bx - ax) * f) * C.TILE_SIZE) + C.TILE_SIZEH;
			int py = (int) ((ay + (by - ay) * f) * C.TILE_SIZE) + C.TILE_SIZEH;
			if (px < win.x1() - C.TILE_SIZE * 2 || px > win.x2() + C.TILE_SIZE * 2 || py < win.y1() - C.TILE_SIZE * 2
					|| py > win.y2() + C.TILE_SIZE * 2)
				continue;
			int cx = px - win.x1() + offX, cy = py - win.y1() + offY;
			// heading over the next few tiles: the route is a staircase of straight and diagonal steps
			int ahead = t.path[Math.min(seg + 4, t.path.length - 1)];
			double vx = ahead % w - ax, vy = ahead / w - ay, len = Math.sqrt(vx * vx + vy * vy);
			DIR dir = len == 0 ? DIR.N : DIR.get(vx / len, vy / len);
			if (t.lane != 0 && len > 0 && t.pos < t.path.length - 4) { // lanes merge back over the last few tiles before the shore
				cx += (int) (-vy / len * t.lane);
				cy += (int) (vx / len * t.lane);
			}
			SETT.HALFENTS().dingy.renderBoat(r, s, cx, cy, dir, System.identityHashCode(t), 0);
			DIR side = dir.perpendicular();
			int x = cx - 12 * C.SCALE + side.x() * 2 * C.SCALE;
			int y = cy - 12 * C.SCALE + side.y() * 2 * C.SCALE;
			if (t.man != null)
				HSprites.STAND.render(t.man.indu(), 0, 0, false, side, null, 0, r, s, ds, x, y);
			else if (t.cargo != null)
				t.cargo.res().renderOneC(r, cx + dir.x() * 2 * C.SCALE, cy + dir.y() * 2 * C.SCALE, 0);
		}
	}

	static int count() {
		return trips.size();
	}

	/** For tests: "x,y step/steps passenger" per boat. */
	static java.util.List<String> where() {
		java.util.List<String> l = new java.util.ArrayList<>();
		for (Trip t : trips) {
			int i = t.path[Math.max(0, Math.min((int) t.pos, t.path.length - 1))];
			String who = t.man != null ? t.man.race().key + "/" + t.man.indu().clas().name : "cargo " + t.cargo.res().key;
			l.add(i % SETT.TWIDTH + "," + i / SETT.TWIDTH + " " + (int) t.pos + "/" + t.path.length + " " + who);
		}
		return l;
	}
}
