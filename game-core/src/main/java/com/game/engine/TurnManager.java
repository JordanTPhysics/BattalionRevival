package com.game.engine;

import com.game.model.Player;

import java.util.ArrayList;
import java.util.List;

public class TurnManager {
    private final List<Player> players;
    private int currentPlayerIndex;
    private int roundNumber;

    public TurnManager(List<Player> players) {
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("At least one player is required.");
        }
        this.players = new ArrayList<>(players);
        this.currentPlayerIndex = 0;
        this.roundNumber = 1;
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public boolean nextTurn() {
        int previousIndex = currentPlayerIndex;
        advanceToNextActivePlayer();
        boolean wrapped = currentPlayerIndex <= previousIndex;
        if (wrapped) {
            roundNumber++;
        }
        return wrapped;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    /**
     * Restores turn clock after reloading an authoritative snapshot (e.g. multiplayer reconnect).
     */
    public void restoreClock(int roundNumber, int currentPlayerIndex) {
        this.roundNumber = Math.max(1, roundNumber);
        if (players.isEmpty()) {
            return;
        }
        this.currentPlayerIndex = Math.floorMod(currentPlayerIndex, players.size());
        if (players.get(this.currentPlayerIndex).isEliminated()) {
            advanceToNextActivePlayer();
        }
    }

    /**
     * Advances to the next non-eliminated seat. When every player is eliminated, leaves the index unchanged.
     */
    public void advanceToNextActivePlayer() {
        if (players.isEmpty()) {
            return;
        }
        int surviving = 0;
        for (Player p : players) {
            if (!p.isEliminated()) {
                surviving++;
            }
        }
        if (surviving == 0) {
            return;
        }
        for (int step = 0; step < players.size(); step++) {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            if (!players.get(currentPlayerIndex).isEliminated()) {
                return;
            }
        }
    }
}
