package com.game.engine;

import com.game.model.Player;
import com.game.model.Position;
import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.map.Tile;
import com.game.model.units.Unit;
import com.game.model.units.UnitType;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarmachineProductionTest {

    @Test
    void adjacentSpawnPrefersOrthogonalRing() {
        GameMap map = new GameMap(20, 15);
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 20; x++) {
                map.setTile(x, y, new Tile(TerrainType.PLAINS_1));
            }
        }
        /*
         * WM at (12,7). Block N,E,S so only west remains for a land spawn.
         */
        placeBlockingUnit(map, 12, 6);
        placeBlockingUnit(map, 13, 7);
        placeBlockingUnit(map, 12, 8);
        Point spawn = com.game.systems.FactorySpawn.findAdjacentSpawn(map, 12, 7, UnitType.Commando);
        assertNotNull(spawn);
        assertEquals(new Point(11, 7), spawn);
    }

    @Test
    void warmachineSpendsPurseNotPlayerMoneyAndSkipsControlChecks() {
        GameMap map = new GameMap(20, 15);
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 20; x++) {
                map.setTile(x, y, new Tile(TerrainType.PLAINS_1));
            }
        }
        PlayableGameSession session = new PlayableGameSession(map);
        Player active = session.getActivePlayer();
        active.setMoney(5000);
        Unit wm = new Unit(UnitType.Warmachine, active, new Position(5, 5));
        active.getUnits().add(wm);
        placeBlockingUnit(map, 5, 4);
        placeBlockingUnit(map, 6, 5);
        placeBlockingUnit(map, 5, 6);
        Tile wmTile = map.getTile(5, 5);
        wmTile.setUnit(wm);
        wmTile.setUnitSpriteId(UnitType.Warmachine.name());
        session.syncUnitTeamMarkerOnTile(wm);
        int treasuryBefore = active.getMoney();
        int purseBefore = wm.getWarmachineFunds();
        int cmdPrice = session.factoryBuildPrice(UnitType.Commando);
        assertTrue(session.canWarmachineProduceUnit(wm, UnitType.Commando));
        assertTrue(session.tryWarmachineBuildUnit(wm, UnitType.Commando));
        assertEquals(treasuryBefore, active.getMoney());
        assertEquals(purseBefore - cmdPrice, wm.getWarmachineFunds());
        Unit built = map.getTile(4, 5).getUnit();
        assertNotNull(built);
        assertEquals(UnitType.Commando, built.getUnitType());
        assertTrue(wm.hasMoved());
    }

    @Test
    void drillOnOreReplenishesPurseAndConsumesAction() {
        GameMap map = new GameMap(20, 15);
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 20; x++) {
                map.setTile(x, y, new Tile(TerrainType.PLAINS_1));
            }
        }
        PlayableGameSession session = new PlayableGameSession(map);
        Player active = session.getActivePlayer();
        Unit wm = new Unit(UnitType.Warmachine, active, new Position(5, 5));
        wm.setWarmachineFunds(100);
        active.getUnits().add(wm);
        Tile t = map.getTile(5, 5);
        t.setUnit(wm);
        t.setOreDeposit(true);
        session.syncUnitTeamMarkerOnTile(wm);
        assertTrue(session.canWarmachineDrill(wm));
        assertTrue(session.tryWarmachineDrill(wm));
        assertEquals(100 + PlayableGameSession.WARMACHINE_DRILL_INCOME, wm.getWarmachineFunds());
        assertTrue(wm.hasMoved());
    }

    private static void placeBlockingUnit(GameMap map, int x, int y) {
        Tile tile = map.getTile(x, y);
        Player p = new Player("Blocker");
        Unit u = new Unit(UnitType.Turret, p, new Position(x, y));
        tile.setUnit(u);
    }
}
