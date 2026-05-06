package com.game.pathfinding;

import com.game.model.Position;
import com.game.model.map.GameMap;

import java.util.Collections;
import java.util.List;

public class AStar {
    public List<Position> findPath(GameMap map, Position start, Position destination) {
        if (map == null || start == null || destination == null) {
            return Collections.emptyList();
        }

        // Placeholder pathfinder. Replace with full A* node expansion.
        return List.of(start, destination);
    }
}
