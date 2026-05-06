package com.game.model.structures;

import com.game.model.Player;
import com.game.model.units.Unit;
import com.game.model.units.UnitAbilities;

public class Structure {
    private final StructureType type;
    private Player owner;
    private int captureProgress;
    private static final int captureRequiredTurns = 2;

    public Structure(StructureType type, Player owner) {
        this.type = type;
        this.owner = owner;
        this.captureProgress = 0;
    }

    public StructureType getType() {
        return type;
    }

    public Player getOwner() {
        return owner;
    }

    public void setOwner(Player owner) {
        this.owner = owner;
        this.captureProgress = 0;
    }

    public boolean canCapture(Unit unit) {
        return unit.getOwner() != getOwner() && unit.hasAbility(UnitAbilities.CONQUEROR);
    }

    public void progressCapture(Unit capturingUnit) {
        if (!canCapture(capturingUnit)) {
            return;
        }
        captureProgress++;
        if (captureProgress >= captureRequiredTurns) {
            setOwner(capturingUnit.getOwner());
        }
    }

    public void resetCaptureProgress() {
        captureProgress = 0;
    }

    public int getIncomePerTurn() {
        return switch (type) {
            case OilRig -> 480;
            case OilAdvanced -> 480;
            case OilRefinery -> 280;
            default -> 0;
        };
    }
}
