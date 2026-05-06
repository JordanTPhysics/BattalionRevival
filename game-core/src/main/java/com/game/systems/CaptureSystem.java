package com.game.systems;

import com.game.model.structures.Structure;
import com.game.model.units.Unit;

public class CaptureSystem {
    public boolean processCapture(Unit unit, Structure structure) {
        if (!structure.canCapture(unit)) {
            return false;
        }

        structure.progressCapture(unit);
        return true;
    }
}
