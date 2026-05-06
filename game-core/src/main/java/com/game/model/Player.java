package com.game.model;

import com.game.model.structures.Structure;
import com.game.model.units.Unit;

import java.util.ArrayList;
import java.util.List;

public class Player {
    private final String name;
    private int money;
    private final List<Unit> units;
    private final List<Structure> structures;
    private boolean eliminated;

    public Player(String name) {
        this.name = name;
        this.money = 0;
        this.units = new ArrayList<>();
        this.structures = new ArrayList<>();
        this.eliminated = false;
    }

    public String getName() {
        return name;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public int getMoney() {
        return money;
    }

    /** Authoritative multiplayer sync — replaces wallet contents. */
    public void setMoney(int money) {
        this.money = Math.max(0, money);
    }

    public void addMoney(int amount) {
        money += amount;
    }

    public boolean spendMoney(int amount) {
        if (money < amount) {
            return false;
        }
        money -= amount;
        return true;
    }

    public List<Unit> getUnits() {
        return units;
    }

    public List<Structure> getStructures() {
        return structures;
    }

    public void resetTurnState() {
        if (eliminated) {
            return;
        }
        for (Unit unit : units) {
            unit.setHasMoved(false);
        }
    }
}
