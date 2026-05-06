package com.game.systems;

import com.game.model.Player;
import com.game.model.structures.Structure;
import com.game.model.structures.StructureType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomySystemTest {

    private final EconomySystem economy = new EconomySystem();

    @Test
    void noIncomeStructuresAlwaysGrantsAtLeastOne() {
        Player p = basePlayer();
        economy.applyTurnIncome(p, 1);
        assertEquals(1, p.getMoney());

        Player q = basePlayer();
        q.getStructures().add(new Structure(StructureType.Factory, q));
        economy.applyTurnIncome(q, 99);
        assertEquals(1, q.getMoney());
    }

    @Test
    void oilRigScalesHigherOnEarlyRounds() {
        Player p = basePlayer();
        p.getStructures().add(new Structure(StructureType.OilRig, p));

        economy.applyTurnIncome(p, 1);
        assertEquals(480, p.getMoney(), "Round 1: max(10%, 100%) of raw income");

        economy.applyTurnIncome(p, 2);
        assertEquals(720, p.getMoney(), "Round 2: adds max(48, 240)");

        Player late = basePlayer();
        late.getStructures().add(new Structure(StructureType.OilRig, late));
        economy.applyTurnIncome(late, 10);
        assertEquals(48, late.getMoney(), "Round 10: plateau at 10% of raw (480)");
    }

    @Test
    void stacksMultipleOilStructuresUsingCombinedRawIncome() {
        Player p = basePlayer();
        p.getStructures().add(new Structure(StructureType.OilRig, p));
        p.getStructures().add(new Structure(StructureType.OilRefinery, p));
        economy.applyTurnIncome(p, 1);

        assertEquals(760, p.getMoney());
        economy.applyTurnIncome(p, 2);
        assertEquals(1140, p.getMoney(), "Second tick uses balance + max(76, 380)");
    }

    @Test
    void invalidTurnNumberClampsLikeRoundOneDivisor() {
        Player p = basePlayer();
        p.getStructures().add(new Structure(StructureType.OilRefinery, p));
        economy.applyTurnIncome(p, 0);

        assertEquals(280, p.getMoney(), "clamp(turn,1)==1 yields full raw for first-scale formula");
    }

    private static Player basePlayer() {
        return new Player("Test");
    }
}
