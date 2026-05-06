package com.game.engine;

import com.game.model.Player;
import com.game.model.map.GameMap;
import com.game.model.map.Tile;
import com.game.model.structures.Structure;
import com.game.model.units.FacingDirection;
import com.game.model.units.Unit;
import com.game.model.units.UnitType;
import com.game.network.protocol.MatchSnapshot;
import com.game.network.protocol.PlayerSnapshot;
import com.game.network.protocol.ProtocolVersions;
import com.game.network.protocol.TileSnapshot;
import com.game.network.protocol.UnitSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds an authoritative {@link MatchSnapshot} from an in-memory {@link PlayableGameSession}.
 */
public final class MatchSnapshotExporter {
    private MatchSnapshotExporter() {
    }

    public static MatchSnapshot export(PlayableGameSession session, String matchId) {
        GameMap map = session.getMap();
        List<Player> players = session.getPlayers();

        List<List<TileSnapshot>> rows = new ArrayList<>(map.getHeight());
        for (int y = 0; y < map.getHeight(); y++) {
            List<TileSnapshot> row = new ArrayList<>(map.getWidth());
            for (int x = 0; x < map.getWidth(); x++) {
                Tile tile = map.getTile(x, y);
                row.add(toTileSnapshot(tile, players));
            }
            rows.add(row);
        }

        List<UnitSnapshot> units = new ArrayList<>();
        for (Player p : players) {
            for (Unit u : p.getUnits()) {
                if (!u.isAlive()) {
                    continue;
                }
                int seat = players.indexOf(p);
                Tile at = map.getTile(u.getPosition().getX(), u.getPosition().getY());
                FacingDirection face = at != null ? at.getUnitFacing() : FacingDirection.EAST;
                units.add(new UnitSnapshot(
                    u.getId(),
                    u.getUnitType().name(),
                    seat,
                    u.getPosition().getX(),
                    u.getPosition().getY(),
                    u.getHealth(),
                    u.hasMoved(),
                    u.isCloaked(),
                    face.name(),
                    u.getUnitType() == UnitType.Warmachine ? u.getWarmachineFunds() : null
                ));
            }
        }

        List<PlayerSnapshot> ps = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            ps.add(new PlayerSnapshot(i, p.getName(), p.getMoney(), p.isEliminated()));
        }

        return new MatchSnapshot(
            ProtocolVersions.MATCH_SNAPSHOT_SCHEMA_VERSION,
            matchId,
            session.getRoundNumber(),
            session.getActivePlayerIndex(),
            map.getTeamCount(),
            map.getWidth(),
            map.getHeight(),
            rows,
            units,
            ps,
            session.matchFinished()
        );
    }

    private static TileSnapshot toTileSnapshot(Tile tile, List<Player> players) {
        if (tile == null) {
            throw new IllegalArgumentException("null tile");
        }
        Structure st = tile.getStructure();
        String structureType = st == null ? null : st.getType().name();
        Integer structureTeam = null;
        if (st != null && st.getOwner() != null) {
            structureTeam = players.indexOf(st.getOwner()) + 1;
        } else if (tile.getStructureTeamId() != null) {
            structureTeam = tile.getStructureTeamId();
        }

        Unit u = tile.getUnit();
        String sprite = tile.getUnitSpriteId();
        Integer unitTeam = tile.getUnitTeamId();
        if (u != null && u.isAlive()) {
            sprite = u.getUnitType().name();
            unitTeam = players.indexOf(u.getOwner()) + 1;
        }

        return new TileSnapshot(
            tile.getTerrainType().name(),
            structureType,
            structureTeam,
            sprite,
            unitTeam,
            tile.getUnitFacing().name(),
            tile.isOreDeposit() ? Boolean.TRUE : null
        );
    }
}
