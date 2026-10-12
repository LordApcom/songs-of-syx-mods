package lordapcom.fts;

import settlement.main.SETT;
import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.RECTANGLE;
import snake2d.util.datatypes.Rec;

/** A small AREA built from a list of tiles, used to drive the game's own placers. */
final class TileSet implements AREA {

	private final Rec body;
	private final boolean[][] in;
	private final int x1, y1;
	private int area;

	/** Bounds are inclusive. */
	TileSet(int x1, int y1, int x2, int y2) {
		this.x1 = x1;
		this.y1 = y1;
		body = new Rec(x1, x2 + 1, y1, y2 + 1);
		in = new boolean[y2 - y1 + 1][x2 - x1 + 1];
	}

	void add(int tx, int ty) {
		if (!in[ty - y1][tx - x1]) {
			in[ty - y1][tx - x1] = true;
			area++;
		}
	}

	@Override
	public RECTANGLE body() {
		return body;
	}

	@Override
	public boolean is(int tile) {
		return is(tile % SETT.TWIDTH, tile / SETT.TWIDTH);
	}

	@Override
	public boolean is(int tx, int ty) {
		int x = tx - x1, y = ty - y1;
		return y >= 0 && y < in.length && x >= 0 && x < in[0].length && in[y][x];
	}

	@Override
	public int area() {
		return area;
	}
}
