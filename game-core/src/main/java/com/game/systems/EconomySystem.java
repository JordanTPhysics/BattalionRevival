package com.game.systems;

import com.game.model.Player;
import com.game.model.structures.Structure;

public class EconomySystem {
    public void applyTurnIncome(Player player, int turnNumber) {
        int safeTurn = Math.max(1, turnNumber);
        double raw = 0;
        for (Structure structure : player.getStructures()) {
            raw += structure.getIncomePerTurn();
        }
        double scaled = Math.max(raw / 10.0, raw / safeTurn);
        int grant = Math.max(1, (int) Math.round(scaled));
        player.addMoney(grant);
    }
}
