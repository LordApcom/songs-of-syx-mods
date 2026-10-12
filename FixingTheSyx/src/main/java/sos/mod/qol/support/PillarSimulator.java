package sos.mod.qol.support;

import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.RECTANGLE;

/**
 * Read-only AREA view that mirrors a backing {@link AREA} except that one specific tile
 * (cx, cy) reports as NOT in the area. Used by the greedy algorithm to score how a
 * hypothetical pillar at (cx, cy) would change the support map without mutating the live
 * embryo.
 */
final class PillarSimulator implements AREA {

    private AREA backing;
    private int cx;
    private int cy;

    PillarSimulator(AREA backing) {
        this.backing = backing;
    }

    /** Swap the underlying AREA without allocating a new simulator. */
    void setBacking(AREA backing) {
        this.backing = backing;
    }

    /** Set the hypothetical "removed" tile. Reusable — no allocation per iteration. */
    void setHole(int cx, int cy) {
        this.cx = cx;
        this.cy = cy;
    }

    @Override
    public boolean is(int tx, int ty) {
        if (tx == cx && ty == cy) return false;
        return backing.is(tx, ty);
    }

    @Override
    public boolean is(int tile) {
        // Known limitation: single-arg is(int) cannot honour the (cx, cy) hole because
        // decoding tile -> (tx, ty) requires the map width, which AREA does not expose.
        // The vanilla support algorithm only calls is(tx, ty), so this delegate is safe
        // for our use case. If a future caller relies on is(int) here, revisit this.
        return backing.is(tile);
    }

    @Override
    public RECTANGLE body() {
        return backing.body();
    }

    @Override
    public int area() {
        return backing.area() - 1;
    }
}
