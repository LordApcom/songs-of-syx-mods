package lordapcom.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

/** Extra command modules. Each module file registers its own names; settlement-screen commands only. */
final class Registry {

	interface Cmd {
		Object run(String[] a) throws Exception;
	}

	static final Map<String, Cmd> CMDS = new LinkedHashMap<>();
	static final Map<String, String> HELP = new LinkedHashMap<>();

	static void add(String name, String usage, Cmd c) {
		CMDS.put(name, c);
		HELP.put(name, usage);
	}

	static {
		ModWorld.register();
		ModPeople.register();
		ModEconomy.register();
		ModDebug.register();
		ModTrade.register();
		ModMods.register();
		ModMilitary.register();
	}

	private Registry() {}
}
