# Network architecture

Authoritative multiplayer uses a thin binary-over-text protocol (JSON envelopes, see `protocol`) over Spring WebSocket (`/ws/match`). The server owns turn resolution and combat validation; the desktop client is a renderer that applies full `MatchSnapshot` payloads.

## Where rules live

- **`game-core`** holds simulation rules (`PlayableGameSession`, movement, combat, economy). This code runs on both client (offline / previews) and server (authoritative application).
- **`AuthoritativeCommandExecutor`** (also in `game-core`) is the server-facing entry point for validated intents (`CsMoveUnit`, `CsAttackUnit`, `CsFactoryBuild`, `CsWarmachineBuild`, `CsWarmachineDrill`, `CsEndTurn`, `CsSurrender`). It must stay aligned with what the UI can emit so duplicate logic does not drift.
- **`protocol`** defines wire types (`NetEnvelope`, snapshots). Bump `ProtocolVersions` when shapes change incompatibly. `MatchSnapshot` / `TileSnapshot` may carry optional **`oreDeposit`** on tiles; `UnitSnapshot` may carry **`warmachineFunds`** for **`Warmachine`** units so purses survive resync.

## Sync model

1. Client connects with `matchId` and `seat` query parameters (handshake attributes on the server).
2. Server sends `ScWelcome` then `ScSnapshot` containing a full `MatchSnapshot`.
3. Each accepted command is broadcast as `ScCommandResult` with `snapshotIfAccepted`; rejected commands carry no snapshot (client keeps last good state).
4. Desktop multiplayer driver (`OnlineMatchCoordinator`) applies snapshots via `PlayableGameSession.fromAuthoritativeSnapshot`, rebinding the Swing panels. Local AI is disabled for online sessions; **End Turn** sends `CsEndTurn` instead of calling `session.endTurn()` locally.

## Maps API

Shared maps are **JSON files** under the directory `battalion.shared-maps.directory` (default `shared-maps/` next to the server working directory). Each upload stores `<slug>.json` plus `<slug>.meta.json` (owner + schema version). `GET /api/maps` lists slugs; `GET /api/maps/{slug}` returns raw map JSON; `POST /api/maps` validates with `MapJsonPersistence.parse` (size cap in controller). **`POST /api/matches`** with `{ "matchId", "mapSlug" }` creates an authoritative room from a catalog slug (idempotent); **`matchId` must not be `demo`** (reserved for the bootstrapped default skirmish).
