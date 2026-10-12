package planahead;

import game.faction.FACTIONS;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprintImp;
import view.main.VIEW;

/**
 * While the player is placing a tech-locked room (i.e. the
 * {@link view.sett.ui.room.construction.UIRoomPlacer} is active with a
 * blueprint whose reqs are not satisfied), the global Planning Mode flag is
 * forced ON. As soon as placement ends or the active blueprint becomes
 * tech-unlocked, the previous user setting is restored.
 *
 * Net effect: the resulting {@link settlement.room.main.construction.ConstructionInstance}
 * is created with active = false (dormant), but variable sizing and every
 * other vanilla placement behavior is preserved.
 */
final class PlanModeWatch {

    private boolean overriding;
    private boolean savedPlanMode;

    void tick() {
        if (VIEW.s() == null) return;
        if (SETT.JOBS() == null) return;

        RoomBlueprintImp current = VIEW.s().ui.placer.blue();
        boolean techLocked = current != null && !current.reqs.passes(FACTIONS.player());

        if (techLocked) {
            if (!overriding) {
                savedPlanMode = SETT.JOBS().planMode.is();
                overriding = true;
            }
            if (!SETT.JOBS().planMode.is()) {
                SETT.JOBS().planMode.set(true);
            }
        } else if (overriding) {
            SETT.JOBS().planMode.set(savedPlanMode);
            overriding = false;
        }
    }
}
