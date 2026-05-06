package com.game.engine;

import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.map.Tile;
import com.game.model.units.Unit;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassThroughMovementTest {

    @Test
    void pathThroughTwoAdjacentFriendliesLeavesBothOnMapAndMoverAtGoal() {
        PlayableGameSession session = newSessionWithLineOfFriendlies();
        GameMap map = session.getMap();
        Unit mover = map.getTile(0, 0).getUnit();
        Unit ally1 = map.getTile(1, 0).getUnit();
        Unit ally2 = map.getTile(2, 0).getUnit();

        List<Point> path = List.of(
            new Point(0, 0),
            new Point(1, 0),
            new Point(2, 0),
            new Point(3, 0)
        );
        assertTrue(session.executeMoveAlongPath(mover, path).accepted());

        assertEquals(mover, map.getTile(3, 0).getUnit());
        assertEquals(ally1, map.getTile(1, 0).getUnit());
        assertEquals(ally2, map.getTile(2, 0).getUnit());
        assertNotNull(map.getTile(1, 0).getUnitSpriteId());
        assertNotNull(map.getTile(2, 0).getUnitSpriteId());
    }

    @Test
    void rewindAfterOnePassThroughStepRestoresAllyUnderfoot() {
        PlayableGameSession session = newSessionWithLineOfFriendlies();
        GameMap map = session.getMap();
        Unit mover = map.getTile(0, 0).getUnit();
        Unit ally1 = map.getTile(1, 0).getUnit();

        List<Point> path = List.of(
            new Point(0, 0),
            new Point(1, 0),
            new Point(2, 0),
            new Point(3, 0)
        );
        session.clearMoveAnimationDisplacementStack();
        session.resetCaptureBeforeMove(mover);
        session.applyMovementStepWithFacing(mover, path.get(0), path.get(1));

        assertEquals(mover, map.getTile(1, 0).getUnit());
        assertNull(map.getTile(0, 0).getUnit());

        session.rewindMovementSteps(mover, path, 1);
        session.completeAnimatedMove(mover);

        assertEquals(mover, map.getTile(0, 0).getUnit());
        assertEquals(ally1, map.getTile(1, 0).getUnit());
    }

    @Test
    void moveAlongPathInterruptedByCloakedEnemyRewindsPassThroughAndLeavesAlliesVisible() {
        GameMap map = plainsMap();
        placeEditorUnit(map, 0, 0, "Commando", 1);
        placeEditorUnit(map, 1, 0, "Scorpion", 1);
        placeEditorUnit(map, 2, 0, "Stealth", 2);
        PlayableGameSession session = new PlayableGameSession(map);
        map.getTile(2, 0).getUnit().setCloaked(true);

        Unit mover = map.getTile(0, 0).getUnit();
        Unit ally1 = map.getTile(1, 0).getUnit();

        List<Point> path = List.of(
            new Point(0, 0),
            new Point(1, 0),
            new Point(2, 0),
            new Point(3, 0)
        );
        assertTrue(session.executeMoveAlongPath(mover, path).accepted());

        assertEquals(mover, map.getTile(0, 0).getUnit());
        assertEquals(ally1, map.getTile(1, 0).getUnit());
        assertNotNull(map.getTile(1, 0).getUnitSpriteId());
    }

    @Test
    void trackerHeadlessMoveInterruptedByCloakChainsDiscoveryStrike() {
        GameMap map = plainsMap();
        placeEditorUnit(map, 0, 0, "Commando", 1);
        placeEditorUnit(map, 1, 0, "Stealth", 2);
        PlayableGameSession session = new PlayableGameSession(map);
        Unit mover = map.getTile(0, 0).getUnit();
        Unit stealth = map.getTile(1, 0).getUnit();
        stealth.setCloaked(true);

        PlayableGameSession.MoveAlongPathOutcome o = session.executeMoveAlongPath(
            mover,
            List.of(new Point(0, 0), new Point(1, 0))
        );
        assertTrue(o.accepted());
        assertEquals(stealth, o.cloakedEnemyRevealed());
        assertFalse(stealth.isCloaked());

        int hpBefore = stealth.getHealth();
        assertTrue(session.tryAttack(mover, stealth, true));
        assertTrue(stealth.getHealth() < hpBefore);
        assertTrue(mover.hasMoved());
    }

    private static PlayableGameSession newSessionWithLineOfFriendlies() {
        GameMap map = plainsMap();
        placeEditorUnit(map, 0, 0, "Commando", 1);
        placeEditorUnit(map, 1, 0, "Scorpion", 1);
        placeEditorUnit(map, 2, 0, "Mortar", 1);
        return new PlayableGameSession(map);
    }

    private static GameMap plainsMap() {
        GameMap map = new GameMap(10, 10);
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                map.setTile(x, y, new Tile(TerrainType.PLAINS_1));
            }
        }
        map.setTeamCount(2);
        return map;
    }

    /** Editor-style placement: sprite + team before {@link PlayableGameSession} wires {@link Unit} instances. */
    private static void placeEditorUnit(GameMap map, int x, int y, String spriteId, int teamId) {
        Tile t = map.getTile(x, y);
        t.setUnitSpriteId(spriteId);
        t.setUnitTeamId(spriteId == null ? null : teamId);
        t.setUnit(null);
    }
}
