package lordapcom.fts;

import snake2d.CORE;
import snake2d.KEYCODES;

/** Raw keyboard reads: the game's own Key objects mis-report modifier chords like Ctrl+Alt. */
final class Keys {

	private Keys() {
	}

	static boolean down(int code) {
		return CORE.getInput().getKeyboard().isPressed(code);
	}

	static boolean ctrl() {
		return down(KEYCODES.KEY_LEFT_CONTROL) || down(KEYCODES.KEY_RIGHT_CONTROL);
	}

	static boolean alt() {
		return down(KEYCODES.KEY_LEFT_ALT) || down(KEYCODES.KEY_RIGHT_ALT);
	}

	static boolean shift() {
		return down(KEYCODES.KEY_LEFT_SHIFT) || down(KEYCODES.KEY_RIGHT_SHIFT);
	}
}
