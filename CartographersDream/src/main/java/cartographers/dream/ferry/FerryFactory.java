package cartographers.dream.ferry;

import java.io.IOException;

import settlement.entity.humanoid.Humanoid;
import settlement.thing.halfEntity.Factory;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.sets.LISTE;

public final class FerryFactory extends Factory<Ferry> {

	public FerryFactory(LISTE<Factory<?>> all) {
		super(all);
	}

	@Override
	protected void save(FilePutter file) {
	}

	@Override
	protected void load(FileGetter file) throws IOException {
	}

	@Override
	protected void clear() {
	}

	@Override
	protected Ferry make() {
		return new Ferry();
	}

	/**
	 * Create and initialise a ferry carrying the given humanoid host along
	 * the given water path. Returns true on success.
	 *
	 * Caller MUST call SETT.ENTITIES().moveIntoTheTheUnknown(host) before
	 * invoking this — otherwise the host stays visible during transit.
	 */
	public boolean make(Humanoid host, int[] pathX, int[] pathY, int disembarkTx, int disembarkTy) {
		Ferry f = create();
		return f.init(host, pathX, pathY, disembarkTx, disembarkTy);
	}

	/**
	 * Create a cosmetic (no-host) ferry — purely visual. Used to indicate
	 * that an actor without a Humanoid representation (settlement caravan
	 * for imports/exports, tourists, etc.) crossed the water to reach the
	 * throne side. The boat sails the path and removes itself at the end.
	 */
	public boolean makeCosmetic(int[] pathX, int[] pathY) {
		Ferry f = create();
		return f.initCosmetic(pathX, pathY);
	}

	/**
	 * Cosmetic ferry that, on arrival, dispatches a deferred create call
	 * into the cargo-owning subsystem (currently Caravans). Used so that
	 * actors materialise AT the moment the boat arrives instead of when
	 * the caller asked.
	 */
	public boolean makeCosmetic(int[] pathX, int[] pathY, byte cargoType, int cargoResIdx, int cargoAmount) {
		Ferry f = create();
		return f.initCosmetic(pathX, pathY, cargoType, cargoResIdx, cargoAmount);
	}

	/**
	 * V71 cargo overload — carries the TRADE_TYPE.all index so the
	 * dispatched Caravan uses the matching trade-type (trade/tax/spoils/
	 * diplomacy). Required since V71 Caravan.init takes TRADE_TYPE.
	 */
	public boolean makeCosmetic(int[] pathX, int[] pathY, byte cargoType, int cargoResIdx, int cargoAmount, byte cargoTType) {
		Ferry f = create();
		return f.initCosmetic(pathX, pathY, cargoType, cargoResIdx, cargoAmount, cargoTType);
	}
}
