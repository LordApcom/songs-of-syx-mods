package lordapcom.wsf;

import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.misc.util.FINDABLE;
import settlement.room.main.RoomInstance;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/** Hides service tiles of rooms whose user filter excludes the humanoid that is searching. */
@Mixin(target = "settlement.path.finders.SFinderRoomService")
public final class FinderMixin {

	// game-src/settlement/path/finders/SFinderRoomService.java:16  public FINDABLE getReservable(int tx, int ty)
	@ModifyReturn(method = "getReservable", params = { int.class, int.class }, require = true)
	public FINDABLE reservable(Object self, FINDABLE vanilla, int tx, int ty) {
		if (vanilla == null || !Filters.anyU)
			return vanilla;
		try {
			Humanoid who = Filters.REQ.get();
			if (who == null)
				return vanilla;
			RoomInstance ins = SETT.ROOMS().map.instance.get(tx, ty);
			if (ins != null && !Filters.mayUse(ins, who))
				return null;
		} catch (Throwable e) {
			Filters.err("FinderMixin", e);
		}
		return vanilla;
	}
}
