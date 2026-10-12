package lordapcom.cdream;

import settlement.path.finders.SFinderEntry;
import settlement.path.path.SPath;
import vizardalpha.polyphon.api.At;
import vizardalpha.polyphon.api.Cancellation;
import vizardalpha.polyphon.api.Inject;
import vizardalpha.polyphon.api.Mixin;
import vizardalpha.polyphon.api.ModifyReturn;

/** Leavers (emigrants, caravans, hunters, emissaries) walk to a dock when the capital is sea-locked. */
@Mixin(SFinderEntry.class)
public final class EntryFinderMixin {

	// game-src settlement/path/finders/SFinderEntry.java:13 find(int,int,SPath,int)
	@Inject(method = "find", params = { int.class, int.class, SPath.class, int.class }, at = At.HEAD, cancellable = true, require = true)
	public void find(Object self, Cancellation ci, int sx, int sy, SPath path, int max) {
		dock(ci, sx, sy, path);
	}

	// game-src settlement/path/finders/SFinderEntry.java:27 any(int,int,SPath,int)
	@Inject(method = "any", params = { int.class, int.class, SPath.class, int.class }, at = At.HEAD, cancellable = true, require = true)
	public void any(Object self, Cancellation ci, int sx, int sy, SPath path, int max) {
		dock(ci, sx, sy, path);
	}

	// game-src settlement/path/finders/SFinderEntry.java:37 anyHas(int,int): hunters need it true to work
	@ModifyReturn(method = "anyHas", params = { int.class, int.class }, require = true)
	public boolean anyHas(Object self, boolean vanilla, int sx, int sy) {
		try {
			return vanilla || Island.isDockSide(sx, sy);
		} catch (Throwable t) {
			Log.once("EntryFinderMixin.anyHas", t);
			return vanilla;
		}
	}

	private static void dock(Cancellation ci, int sx, int sy, SPath path) {
		try {
			if (Island.isDockSide(sx, sy))
				ci.cancel(Island.pathToDock(sx, sy, path));
		} catch (Throwable t) {
			Log.once("EntryFinderMixin.find", t);
		}
	}
}
