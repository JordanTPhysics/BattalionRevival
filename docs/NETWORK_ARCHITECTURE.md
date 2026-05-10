# Network architecture

Authoritative multiplayer uses a thin binary-over-text protocol (JSON envelopes, see `protocol`) over Spring WebSocket (`/ws/match`). The server owns turn resolution and combat validation; the desktop client is a renderer that applies full `MatchSnapshot` payloads.

## Where rules live

- **`game-core`** holds simulation rules (`PlayableGameSession`, movement, combat, economy). This code runs on both client (offline / previews) and server (authoritative application).
- **`AuthoritativeCommandExecutor`** (also in `game-core`) is the server-facing entry point for validated intents (`CsMoveUnit`, `CsAttackUnit`, `CsMoveAndAttackUnit`, `CsFactoryBuild`, `CsWarmachineBuild`, `CsWarmachineDrill`, `CsEndTurn`, `CsSurrender`). It must stay aligned with what the UI can emit so duplicate logic does not drift.
- **`protocol`** defines wire types (`NetEnvelope`, snapshots). Bump `ProtocolVersions` when shapes change incompatibly. `MatchSnapshot` / `TileSnapshot` may carry optional **`oreDeposit`** on tiles; `UnitSnapshot` may carry **`warmachineFunds`** for **`Warmachine`** units so purses survive resync.

## Sync model

1. Client connects with `matchId` and `seat` query parameters (handshake attributes on the server).
2. Server sends `ScWelcome` then `ScSnapshot` containing a full `MatchSnapshot`.
3. Each accepted command is broadcast as `ScCommandResult` with `snapshotIfAccepted`; rejected commands carry no snapshot (client keeps last good state).
4. Desktop multiplayer driver (`OnlineMatchCoordinator`) applies snapshots via `PlayableGameSession.fromAuthoritativeSnapshot`, rebinding the Swing panels. Local AI is disabled for online sessions; **End Turn** sends `CsEndTurn` instead of calling `session.endTurn()` locally. For full multiplayer sync, issue **all** player actions through the coordinator request methods so the server broadcasts updated snapshots (see **Browser client** below).

## Maps API

Shared maps are **JSON files** under the directory `battalion.shared-maps.directory` (default `shared-maps/` next to the server working directory). Each upload stores `<slug>.json` plus `<slug>.meta.json` (owner + schema version). `GET /api/maps` lists slugs; `GET /api/maps/{slug}` returns raw map JSON; `POST /api/maps` validates with `MapJsonPersistence.parse` (size cap in controller). **`POST /api/matches`** with `{ "matchId", "mapSlug" }` creates an authoritative room from a catalog slug (idempotent); **`matchId` must not be `demo`** (reserved for the bootstrapped default skirmish).

## Browser client (`web-client/`)

A **Vite + React + TypeScript** SPA supports lobby/join, Canvas-based play, and a JSON map editor. Configure `VITE_SERVER_ORIGIN` (see `.env.development`) so REST and WebSocket URLs point at the Battalion server.

- **Guests**: a stable `guestId` and optional display name are stored in `localStorage`; map uploads set `ownerUsername` from that profile until real auth exists.
- **REST CORS**: `WebCorsConfig` registers CORS for `/api/**`. Override allowed origins with `battalion.cors.allowed-origins` (comma-separated) in `application.properties`. The default includes `http://localhost:5173` for Vite.
- **Commands**: the web client sends the full command set (`CsMoveUnit`, `CsAttackUnit`, `CsFactoryBuild`, `CsWarmachineBuild`, `CsWarmachineDrill`, `CsEndTurn`, `CsSurrender`) and applies `ScCommandResult` / `ScSnapshot` like the protocol describes.
- **Desktop coordinator**: `OnlineMatchCoordinator` exposes `requestMoveUnit`, `requestAttack`, `requestFactoryBuild`, `requestWarmachineBuild`, `requestWarmachineDrill`, `requestEndTurn`, and `requestSurrender` for Swing integration; gameplay should call these instead of only mutating a local session when you want server-authoritative multiplayer. Rejected commands surface via `ScCommandResult` and the coordinator forwards `reasonCode` + `detail` to the issue callback.

## Assets for the web build

Terrain and other PNGs are copied from `game-core/src/main/resources/assets` into `web-client/public/assets` by `npm run copy-assets` (runs before `dev` / `build`).
