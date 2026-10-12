package lordapcom.bridge;

import java.util.ArrayList;
import java.util.List;

import snake2d.util.sets.LIST;

/**
 * The game's LISTs hand out one shared iterator, so a for-each over a list inside another for-each over the same
 * list (often hidden inside a game call) never ends. Iterate a snapshot instead.
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
