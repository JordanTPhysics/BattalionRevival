package com.game.engine;

import com.game.model.Player;
import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.map.Tile;
import com.game.model.structures.Structure;
import com.game.model.structures.StructureType;
import com.game.model.units.FacingDirection;
import com.game.network.protocol.MatchSnapshot;
import com.game.network.protocol.TileSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a runtime {@link GameMap} from an authoritative {@link MatchSnapshot} before units hydrate.
 */
public final class SnapshotGameMapBuilder {
    private SnapshotGameMapBuilder() {
    }

    public static GameMap build(MatchSnapshot snapshot) {
        GameMap map = new GameMap(snapshot.width(), snapshot.height());
        map.setTeamCount(snapshot.teamCount());

        List<List<TileSnapshot>> tiles = snapshot.tiles();
        if (tiles == null || tiles.size() != snapshot.height()) {
            throw new IllegalArgumentException("Snapshot tile rows mismatch height");
        }
        for (int y = 0; y < snapshot.height(); y++) {
            List<TileSnapshot> row = tiles.get(y);
            if (row == null || row.size() != snapshot.width()) {
                throw new IllegalArgumentException("Snapshot tile row width mismatch at y=" + y);
            }
            for (int x = 0; x < snapshot.width(); x++) {
                TileSnapshot ts = row.get(x);
                Tile tile = new Tile(TerrainType.valueOf(ts.terrain()));
                map.setTile(x, y, tile);

                if (ts.structure() != null && !ts.structure().isBlank()) {
                    tile.setStructure(new Structure(StructureType.valueOf(ts.structure()), null));
                    tile.setStructureTeamId(ts.structureTeam());
                } else {
                    tile.setStructure(null);
                    tile.setStructureTeamId(null);
                }
                tile.setUnitSpriteId(ts.unitSprite());
                tile.setUnitTeamId(ts.unitTeam());
                if (ts.unitFacing() != null && !ts.unitFacing().isBlank()) {
                    tile.setUnitFacing(FacingDirection.valueOf(ts.unitFacing()));
                } else {
                    tile.setUnitFacing(FacingDirection.EAST);
                }
                tile.setOreDeposit(Boolean.TRUE.equals(ts.oreDeposit()));
            }
        }
        return map;
    }

    /** Convenience when tiles arrive row-major as flat list. */
    public static GameMap buildFlat(MatchSnapshot snapshot, List<TileSnapshot> flatRowMajor) {
        int w = snapshot.width();
        int h = snapshot.height();
        List<List<TileSnapshot>> rows = new ArrayList<>(h);
        int idx = 0;
        for (int y = 0; y < h; y++) {
            List<TileSnapshot> row = new ArrayList<>(w);
            for (int x = 0; x < w; x++) {
                row.add(flatRowMajor.get(idx++));
            }
            rows.add(row);
        }
        return build(new MatchSnapshot(
            snapshot.schemaVersion(),
            snapshot.matchId(),
            snapshot.roundNumber(),
            snapshot.activePlayerIndex(),
            snapshot.teamCount(),
            snapshot.width(),
            snapshot.height(),
            rows,
            snapshot.units(),
            snapshot.players(),
            snapshot.matchFinished()
        ));
    }
}
