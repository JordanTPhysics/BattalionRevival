package com.game.engine;

import com.game.model.Player;
import com.game.model.units.Unit;
import com.game.model.units.UnitType;
import com.game.network.protocol.CsAttackUnit;
import com.game.network.protocol.CsEndTurn;
import com.game.network.protocol.CsFactoryBuild;
import com.game.network.protocol.CsMoveUnit;
import com.game.network.protocol.CsSurrender;
import com.game.network.protocol.CsWarmachineBuild;
import com.game.network.protocol.CsWarmachineDrill;
import com.game.network.protocol.GridPoint;
import com.game.network.protocol.MatchSnapshot;
import com.game.network.protocol.NetEnvelope;
import com.game.network.protocol.ProtocolVersions;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies validated player intents through {@link PlayableGameSession} — intended for the authoritative server.
 */
public final class AuthoritativeCommandExecutor {

    public record CommandApplyResult(boolean accepted, String reasonCode, String detail, MatchSnapshot snapshot) {
        public static CommandApplyResult reject(String code, String detail) {
            return new CommandApplyResult(false, code, detail, null);
        }

        public static CommandApplyResult ok(MatchSnapshot snapshot) {
            return new CommandApplyResult(true, null, null, snapshot);
        }
    }

    private AuthoritativeCommandExecutor() {
    }

    public static CommandApplyResult execute(
        PlayableGameSession session,
        int issuingSeatIndex,
        String expectedMatchId,
        NetEnvelope envelope
    ) {
        if (envelope.protocolVersion() != ProtocolVersions.NETWORK_PROTOCOL_VERSION) {
            return CommandApplyResult.reject("BAD_PROTOCOL", "Client protocol version mismatch");
        }
        if (session.matchFinished()) {
            return CommandApplyResult.reject("MATCH_DONE", "Match already finished");
        }

        if (envelope instanceof CsMoveUnit m) {
            return handleMove(session, issuingSeatIndex, expectedMatchId, m);
        }
        if (envelope instanceof CsAttackUnit a) {
            return handleAttack(session, issuingSeatIndex, expectedMatchId, a);
        }
        if (envelope instanceof CsFactoryBuild b) {
            return handleFactory(session, issuingSeatIndex, expectedMatchId, b);
        }
        if (envelope instanceof CsWarmachineBuild wb) {
            return handleWarmachineBuild(session, issuingSeatIndex, expectedMatchId, wb);
        }
        if (envelope instanceof CsWarmachineDrill wd) {
            return handleWarmachineDrill(session, issuingSeatIndex, expectedMatchId, wd);
        }
        if (envelope instanceof CsEndTurn e) {
            return handleEndTurn(session, issuingSeatIndex, expectedMatchId, e);
        }
        if (envelope instanceof CsSurrender s) {
            return handleSurrender(session, issuingSeatIndex, expectedMatchId, s);
        }
        return CommandApplyResult.reject("UNSUPPORTED", "Command type not executable here");
    }

    private static CommandApplyResult handleMove(
        PlayableGameSession session,
        int seatIndex,
        String expectedMatchId,
        CsMoveUnit command
    ) {
        if (!expectedMatchId.equals(command.matchId())) {
            return CommandApplyResult.reject("MATCH_ID", "Stale match id");
        }
        if (!isActiveSeat(session, seatIndex)) {
            return CommandApplyResult.reject("NOT_YOUR_TURN", "Seat cannot act now");
        }
        Unit unit = findUnit(session, command.unitId());
        if (unit == null || unit.getOwner() != session.getPlayers().get(seatIndex)) {
            return CommandApplyResult.reject("BAD_UNIT", "Unit not found or not owned");
        }
        List<Point> path = new ArrayList<>();
        for (GridPoint gp : command.pathIncludingStart()) {
            path.add(new Point(gp.x(), gp.y()));
        }
        PlayableGameSession.MoveAlongPathOutcome moved = session.executeMoveAlongPath(unit, path);
        if (!moved.accepted()) {
            return CommandApplyResult.reject("ILLEGAL_MOVE", "Path rejected by movement rules");
        }
        session.finishActionAfterMoveAlongPath(unit, moved);
        return CommandApplyResult.ok(MatchSnapshotExporter.export(session, expectedMatchId));
    }

    private static CommandApplyResult handleAttack(
        PlayableGameSession session,
        int seatIndex,
        String expectedMatchId,
        CsAttackUnit command
    ) {
        if (!expectedMatchId.equals(command.matchId())) {
            return CommandApplyResult.reject("MATCH_ID", "Stale match id");
        }
        if (!isActiveSeat(session, seatIndex)) {
            return CommandApplyResult.reject("NOT_YOUR_TURN", "Seat cannot act now");
        }
        Unit attacker = findUnit(session, command.attackerUnitId());
        Unit defender = findUnit(session, command.defenderUnitId());
        if (attacker == null || defender == null) {
            return CommandApplyResult.reject("BAD_UNIT", "Combatant not found");
        }
        if (attacker.getOwner() != session.getPlayers().get(seatIndex)) {
            return CommandApplyResult.reject("BAD_UNIT", "Attacker not owned");
        }
        if (!session.tryAttack(attacker, defender)) {
            return CommandApplyResult.reject("ILLEGAL_ATTACK", "Attack rejected by combat rules");
        }
        return CommandApplyResult.ok(MatchSnapshotExporter.export(session, expectedMatchId));
    }

    private static CommandApplyResult handleFactory(
        PlayableGameSession session,
        int seatIndex,
        String expectedMatchId,
        CsFactoryBuild command
    ) {
        if (!expectedMatchId.equals(command.matchId())) {
            return CommandApplyResult.reject("MATCH_ID", "Stale match id");
        }
        if (!isActiveSeat(session, seatIndex)) {
            return CommandApplyResult.reject("NOT_YOUR_TURN", "Seat cannot act now");
        }
        UnitType type;
        try {
            type = UnitType.valueOf(command.unitType());
        } catch (IllegalArgumentException ex) {
            return CommandApplyResult.reject("BAD_TYPE", "Unknown unit type");
        }
        if (!session.tryFactoryBuildUnit(command.factoryX(), command.factoryY(), type)) {
            return CommandApplyResult.reject("ILLEGAL_BUILD", "Factory build rejected");
        }
        return CommandApplyResult.ok(MatchSnapshotExporter.export(session, expectedMatchId));
    }

    private static CommandApplyResult handleWarmachineBuild(
        PlayableGameSession session,
        int seatIndex,
        String expectedMatchId,
        CsWarmachineBuild command
    ) {
        if (!expectedMatchId.equals(command.matchId())) {
            return CommandApplyResult.reject("MATCH_ID", "Stale match id");
        }
        if (!isActiveSeat(session, seatIndex)) {
            return CommandApplyResult.reject("NOT_YOUR_TURN", "Seat cannot act now");
        }
        Unit wm = findUnit(session, command.warmachineUnitId());
        if (wm == null || wm.getOwner() != session.getPlayers().get(seatIndex)) {
            return CommandApplyResult.reject("BAD_UNIT", "War Machine not found or not owned");
        }
        UnitType type;
        try {
            type = UnitType.valueOf(command.unitType());
        } catch (IllegalArgumentException ex) {
            return CommandApplyResult.reject("BAD_TYPE", "Unknown unit type");
        }
        if (!session.tryWarmachineBuildUnit(wm, type)) {
            return CommandApplyResult.reject("ILLEGAL_BUILD", "War Machine fabrication rejected");
        }
        return CommandApplyResult.ok(MatchSnapshotExporter.export(session, expectedMatchId));
    }

    private static CommandApplyResult handleWarmachineDrill(
        PlayableGameSession session,
        int seatIndex,
        String expectedMatchId,
        CsWarmachineDrill command
    ) {
        if (!expectedMatchId.equals(command.matchId())) {
            return CommandApplyResult.reject("MATCH_ID", "Stale match id");
        }
        if (!isActiveSeat(session, seatIndex)) {
            return CommandApplyResult.reject("NOT_YOUR_TURN", "Seat cannot act now");
        }
        Unit wm = findUnit(session, command.warmachineUnitId());
        if (wm == null || wm.getOwner() != session.getPlayers().get(seatIndex)) {
            return CommandApplyResult.reject("BAD_UNIT", "War Machine not found or not owned");
        }
        if (!session.tryWarmachineDrill(wm)) {
            return CommandApplyResult.reject("ILLEGAL_DRILL", "Ore drilling rejected");
        }
        return CommandApplyResult.ok(MatchSnapshotExporter.export(session, expectedMatchId));
    }

    private static CommandApplyResult handleEndTurn(
        PlayableGameSession session,
        int seatIndex,
        String expectedMatchId,
        CsEndTurn command
    ) {
        if (!expectedMatchId.equals(command.matchId())) {
            return CommandApplyResult.reject("MATCH_ID", "Stale match id");
        }
        if (!isActiveSeat(session, seatIndex)) {
            return CommandApplyResult.reject("NOT_YOUR_TURN", "Seat cannot act now");
        }
        session.endTurn();
        return CommandApplyResult.ok(MatchSnapshotExporter.export(session, expectedMatchId));
    }

    private static CommandApplyResult handleSurrender(
        PlayableGameSession session,
        int seatIndex,
        String expectedMatchId,
        CsSurrender command
    ) {
        if (!expectedMatchId.equals(command.matchId())) {
            return CommandApplyResult.reject("MATCH_ID", "Stale match id");
        }
        Player p = session.getPlayers().get(seatIndex);
        session.surrender(p);
        return CommandApplyResult.ok(MatchSnapshotExporter.export(session, expectedMatchId));
    }

    private static boolean isActiveSeat(PlayableGameSession session, int seatIndex) {
        return seatIndex == session.getActivePlayerIndex();
    }

    private static Unit findUnit(PlayableGameSession session, String unitId) {
        if (unitId == null) {
            return null;
        }
        for (Player pl : session.getPlayers()) {
            for (Unit u : pl.getUnits()) {
                if (unitId.equals(u.getId())) {
                    return u;
                }
            }
        }
        return null;
    }
}
