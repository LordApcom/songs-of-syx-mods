package lordapcom.cdream;

import java.util.ArrayList;
import java.util.List;

import snake2d.util.sets.LIST;

/**
 * The game's LISTs hand out one shared iterator: a for-each over a list while the game (or another loop) is
 * walking the same list breaks the outer walk. Iterate a snapshot instead.
 */
final class Lists {

	static <T> List<T> of(LIST<T> l) {
		ArrayList<T> r = new ArrayList<>(l.size());
		for (int i = 0; i < l.size(); i++)
			r.add(l.get(i));
		return r;
	}

	private Lists() {}
}
