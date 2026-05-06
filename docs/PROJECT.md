# Battalion Revival — project overview

Java desktop turn-based tactics inspired by *Battalion: Nemesis*. The playable client is **Swing-based** with a dark military HUD, procedural battlefield overlays (movement reach, attack bands), WAV combat/movement audio, and optional AI opponents.

This document reflects **behavior implemented in code** as of the current tree—not the original design brief.

---

## Running

- **Application**: Gradle application plugin; main class `com.game.engine.GameEngine`.
- **Typical**: `./gradlew run` (Windows: `gradlew.bat run`) from the project root—working directory should allow resolving relative paths such as `maps/`.
- **Optional CLI argument**: path to a map JSON file. If omitted, loads `maps/default.json` when that file exists; otherwise builds a plain **20×20 PLAINS_1** skirmish field.
- **Java**: 17 (see `build.gradle`).

Example:

```bash
gradlew run --args="maps/my-map.json"
```

---

## High-level architecture

| Layer | Role |
|-------|------|
| **`com.game.model`** | Pure data: `GameMap`, `Tile`, `TerrainType`, `Structure` / `StructureType`, `Unit` / `UnitType`, `Player`, `Position`. |
| **`com.game.persistence`** | Map JSON load/save (`MapJsonPersistence`). |
| **`com.game.systems`** | Rules without UI: `CombatSystem`, `MovementSystem`, `MovementReach`, `EconomySystem`, `CaptureSystem` usage inlined in session, `CombatTerrain`, `FactorySpawn`, `JammingRules`. |
| **`com.game.pathfinding`** | Path validation / search (`AStar`, `GridPathfinder`, `UnitMovementPaths`). |
| **`com.game.engine`** | Session orchestration: `PlayableGameSession` (full rules), `TurnManager`, `GameEngine` (minimal headless/bootstrap used only from `main`). |
| **`com.game.engine.ai`** | AI turn scheduling and actions (`AiEngine`, `AiTurnExecutor`, `AiStepRunner`, heuristics + classpath config). |
| **`com.game.ui`** | Swing windows, theme, assets, dialogs. Map files are read/written via `MapJsonPersistence`. |

**Separation**: gameplay decisions live in `PlayableGameSession` and systems; `GameWindow` drives presentation and calls session APIs.

---

## Players and turns

- **Seats**: Up to **four** named factions — Red, Blue, Green, Yellow — driven by `GameMap.getTeamCount()` (clamped 2–4).
- **Turn order**: Cyclic over non-eliminated seats (`TurnManager.advanceToNextActivePlayer`).
- **Round number**: `TurnManager.getRoundNumber()` increments whenever play wraps from the last active seat back toward the start of the cycle. The HUD label **“TURN N”** displays this **round** index (full cycle counter), not “clicks per faction.”
- **Per-unit action**: Each unit has at most **one action per turn**: move, attack, or move-then-attack (where rules allow). Consumption is tracked with `Unit.hasMoved()` / session helpers.
- **Round-start phase** (once per full round, before the first seat acts in that round): `EconomySystem.applyTurnIncome` for every surviving player; Maintenance / Resupply healing ticks (`PlayableGameSession.applyRoundStartEconomyAndAbilities`).
- **End of each seat’s turn**: `PlayableGameSession.endTurn()` resolves **structure capture progress** for that active player’s units on capturable tiles, then advances to the next seat; if a new round began, economy + abilities run as above.

---

## Win, loss, and elimination

- **Match end**: When **at most one** non-eliminated player remains (`matchFinished()`).
- **Victory**: Sole survivor; UI shows a dialog and top-bar pill (“WINNER”). If everyone is eliminated with no survivor, the game reports **no winner** (“NO SURVIVORS” / stalemate messaging).
- **Elimination triggers** (`shouldBeEliminatedFromUnitState`):
  - No living units.
  - Team once fielded a **Kingpin** unit and now has **no** living Kingpin (`kingpinEligibleTeams` tracking).
  - No living units that are **not** tagged **Aimless** (Aimless-only armies do not count as “surviving combat units” for defeat checks).
- **Capital (`StructureType.Capital`)**: Captured like other conquerable structures; when ownership flips, the previous owner is eliminated (capital structure removed from map; other structures may transfer or neutralize).

---

## Map format (`maps/*.json`)

Handled by `MapJsonPersistence` (`com.game.persistence`):

- Top-level: `width`, `height` (each **10–40**), optional `teamCount` (defaults to 2 if missing), `tiles` as **row-major** arrays.
- Each tile object:
  - `terrain`: **`TerrainType` enum name** (UPPER_SNAKE).
  - `structure`: `StructureType` enum name or `null`.
  - `structureTeam`: integer **1–N** or `null` (neutral). Wired to `players.get(teamId - 1)` at session start.
  - `unitSprite`: string ID for sprite sheet lookup, or `null`.
  - `unitTeam`: integer team index for placed units, or `null`.
  - `unitFacing`: `FacingDirection` enum name or `null`.
  - `oreDeposit`: boolean (**optional**; when omitted, treated as false). Tiles where **Warmachines** may **Drill** for onboard funds while standing on them.

**Spawn rule**: If `unitSprite` is set and no runtime `Unit` exists yet, `PlayableGameSession.wireUnitsFromMap` creates a `Unit` via `UnitSpriteMapper.inferUnitType(spriteId)` and assigns ownership from `unitTeam` (default **1**).

---

## Terrain (`TerrainType`)

The game uses **many** distinct terrain variants (plains, forests, hills, mountains, sea, reefs, shores, bridges, canyons, archipelago, rock formations), each backed by a PNG **asset stem** (see `TerrainType.assetStem()`).

- **Defense**: Each tile contributes a **percentage damage reduction** via `getDefenseModifier()` (shown in the HUD as “Defense +X%”).
- **Movement cost / passability**: Derived from `MovementKind` (FOOT, WHEELED, TRACKED, NAVAL, AIR). Air pays **1** per tile and ignores most blocking.
- **Cover structures**: `StructureType` exposes `getCover()` used in combat where applicable (see structure interactions in combat/session code paths).
- **Ranged bonus**: Units with ranged combat (`attackRange > 1` with a real weapon per `CombatTerrain.isRangedAttacker`) gain **+1 max Manhattan range** when attacking from **`HILLS_1` / `HILLS_2`** (`grantsRangedHillRangeBonus()`).
- **Shores**: Tiles whose names start with `SHORE_` are treated as **coastal** for **Sea Control + factory naval production** and **Leviathan** conversion eligibility (`isCoastalShoreForSeaFactory()`).
- **Massive hull**: Units with **MassiveHull** cannot enter shore tiles (`TerrainType.canTraverse(Unit)`).

HUD tile panel uses **foot infantry** (`UnitType.Commando`) as the reference for “move cost (foot)” when labeling a tile.

---

## Structures (`StructureType`)

| Type | Notes |
|------|--------|
| **Capital** | HQ; losing it via capture eliminates the prior owner. Cannot be placed as neutral in the editor when brush faction is Neutral. |
| **Factory** | Production building; opens **Production Console** when the active player selects an **empty** owned factory tile. One build per factory per turn; blocked if any unit occupies the factory tile. |
| **GroundControl**, **SeaControl**, **AirControl** | Gate factory categories (land / naval / air). Naval builds additionally require a **coastal** factory tile. |
| **OilRig**, **OilAdvanced** | **+$480** income per full round (`Structure.getIncomePerTurn`). |
| **OilRefinery** | **+$280** per round. |

**Capture**: Only units with **Conqueror** may capture enemy or neutral structures. Progress increments when the capturer **ends their turn** on the tile; **two** successful end-turn ticks flip ownership (`captureRequiredTurns = 2`). Leaving the tile resets progress (`resetCaptureProgress`). Ownership syncs structure lists on `Player` and JSON-facing `structureTeamId` on tiles.

---

## Units (`UnitType`)

Each enum constant defines starting HP, attack power, movement speed, attack range, armor type, attack type, movement kind, and ability list (`UnitType.getAbilities()`).

**Categories**:

- **Movement kinds**: FOOT, WHEELED, TRACKED, NAVAL, AIR — drive terrain tables.
- **Armor / attack types**: `Unit.ArmorType` and `Unit.AttackType` — drive the combat multiplier matrix.
- **Stationary types**: `movementSpeed == 0` (e.g. Blockade, Turret) do not receive factory production entries in the UI.
- **Transports**: **Albatross** (air), **Leviathan** (naval) — **not** built from factories; created by **right-click context menu** conversion from eligible land units (`PlayableGameSession` + `Unit.convertToTransport` / revert APIs).

**Factory catalog**: `FactoryBuildDialog` groups buildable types into Land / Sea / Air (`UnitType.FactoryBuildCategory`), excludes transports and zero-move types, and excludes **Warmachine** from the factory grid.

**Warmachine (`UnitType.Warmachine`)** — forged in the field, not at factories:

- **War purse**: starts at **`Unit.WARMACHINE_STARTING_FUNDS` ($2000)** on the unit; pays **`factoryBuildPrice(type)`** from that purse only (faction **HQ money** is untouched).
- **No control structures**: land / sea / air categories do **not** require Ground, Sea, or Air control; only a valid **orthogonal adjacent** empty spawn the produced type can traverse (`FactorySpawn.findAdjacentSpawn`), same movement-kind rules as factory spawn checks.
- **Turn cost**: fabricating or drilling **consumes the Warmachine’s action** for the turn; built units cannot act the turn they appear (same as factory output).
- **UI**: right-click an eligible friendly Warmachine → **Fabricate unit…** (`WarmachineBuildDialog`) or **Drill ore deposit** when standing on `Tile.isOreDeposit()` (payout `PlayableGameSession.WARMACHINE_DRILL_INCOME`).
- **Kingpin**: still tied to team elimination when all Kingpin units are lost (see win/loss above).

**Pricing** (`factoryBuildPrice`):  
`round(max(50, (startingHealth/2 + damage*5) * multipliers))` with multipliers for armor heaviness, heavy attack type, and air speed factor.

---

## Engagement and combat

**Who can shoot whom** (`EngagementRules.attackerCanTargetDefender`):

- Cloaked defenders cannot be targeted until revealed (Tracker discovery path uncloaks before damage).
- **Aircraft**: Ground/naval attackers need **AntiAir**; air-on-air always allowed.
- **U-boat**: Only attackers with **AntiSubmarine** may target.
- **U-boat attacker**: May only target **naval** defenders.

**Strike flow** (`CombatSystem`):

- Outgoing damage uses armor/attack-type matrix, ability modifiers (Blitzkrieg, Tracker discovery, RapidFire vs light armor, Explosive vs heavy / on-structure targets, Cloaker double damage while cloaked), **Behemoth** counter penalty, then **stepped attacker HP effectiveness**: full damage only above ~75% HP; 75%, 50%, 25% bands floor effectiveness until ≤25% HP (`attackerHealthRatio`).
- **Stunning** prevents enemy **counterattack**.
- **Stalwart**: defined on ability constants and combat (`applyDamageWithStalwart`) — **20%** chance to survive a lethal hit at **1 HP** if the unit has the ability (currently **no** `UnitType` ships Stalwart in `getAbilities()`, but the rule exists for future/content use).
- **Piercing**: After the primary hit, **60%** of rolled outgoing damage applies to a live enemy **behind** the defender on the same orthogonal line.
- **Counters**: Require alive defender, minimum/maximum Manhattan range including hills bonus for ranged (`CombatTerrain.effectiveMaxAttackRange`), and swapped engagement check so dead zones and anti-air/sub rules apply fairly.

**Minimum range (“dead zone”)**: For `attackRange > 1`, `Unit.getMinAttackRange()` defines closest tiles that cannot be attacked or countered (`ceil(maxRange/2 - 1)`). HUD shows `"min–max"` via `getAttackRangeDisplayString()`.

---

## Special systems

- **Jamming** (`JammingRules`): Jammer projects Manhattan distance **2**; blocks aircraft movement through affected tiles and decloaks hidden units when relevant.
- **Cloaker**: After moving, unit cloaks; adjacent enemy proximity decloaks; double damage if attacking while cloaked.
- **Tracker**: Moving onto a cloaked enemy interrupts movement, reveals, and enables a boosted discovery strike path in session/UI animation.
- **Scavenger**: If the primary attack **destroys** its target, the attacker’s action may remain available for further move/attack this turn (session bookkeeping).

---

## UI — screens

### Start menu (`StartMenuWindow`)

- Dark backdrop card with accent stripe; **Play Skirmish** opens `MapSelectionDialog` (`maps/` listing).
- After map load: **per-seat Human/AI** selector (defaults seat 1 Human, others AI).
- **Map Builder** opens `MapBuilderWindow`; closing returns to menu. Builder may update the menu’s in-memory session map + display name when maps are loaded from disk.
- Footer shows **v0.1.0 — Tactical Revival Build**.

### Game (`GameWindow`)

- **Center**: Scrollable `GameMapPanel` — terrain, structures, units, movement path drafting, reach overlays, combat glide animation, explosion effects, factory idle crosshair when eligible, **small gold corner marker** on ore-deposit tiles.
- **Top bar**: Mission name (uppercase kicker), **TURN** (round index), faction pill (accent per Red/Blue/Green/Yellow), **FUNDS** for displayed player (active player while ongoing; winner at end), **ZOOM** ±, **VOL** master slider (`ClasspathWavPlayer`), **End Turn**, **Exit Mission**.
- **Bottom** (`GameInfoPanel`, ~210px): Three titled HUD columns:
  - **Mission**: Level title, field dimensions × faction count, **Active team · N** (1-based seat index).
  - **Tile**: Grid coords, terrain pretty-name, defense % and foot move cost or “Impassable (foot)”, structure line + capture hint for foot units.
  - **Unit**: Type, owner, HP, move, range string, power, armor, attack type, friendly action status (**ready**/**used**) or hostile marker; **ability glyphs** with rich HTML tooltips from `AbilityPresentation`.
- **Edge pan**: `ViewportEdgePanSupport` on the scroll pane (and factory overlay scroll).
- **Factory**: Click **owned, empty** factory → fullscreen-themed **`FactoryBuildDialog`** with scroll-edge panning, ESC to close, categorized ONLINE/OFFLINE panels.
- **Warmachine**: Right-click **your** Warmachine (before it has acted) → **Fabricate unit…** or **Drill ore deposit** when applicable; uses `PlayableGameSession` APIs above.

### Map builder (`MapBuilderWindow` / `MapBuilderPanel`)

- **West toolbar** (fixed ~288px): terrain / structure / unit brush combos with texture previews, **Resources → Ore deposit brush** (LMB toggles `oreDeposit` without changing terrain), faction brush (Neutral + teams 1–4), **Factions On Map** spinner, bulk actions menu (fill terrain, clear structures/units, destructive reset to plains), map name field, Save / Load JSON, Exit Builder.
- **Map corner**: Grid width×height spinners + Apply, zoom % + buttons.
- **Painting**: Left drag paints; **right-click or Shift+click** clears structure, unit, and **ore flag** on tile. Capital cannot be painted neutral.
- **Padding**: `EDGE_PADDING_TILES = 10` empty margin around the grid for comfortable panning.
- **Zoom**: Tile size 16–96px (builder) vs 14–84px (game panel)—percent labels are relative to each surface’s base tile size.

---

## AI

- **`AiEngine`** receives the set of **AI-controlled seat indices** (from the menu or defaults: all except seat 0).
- **`AiTurnExecutor`** sequences actions with EDT-safe delays between steps (`AI_STEP_DELAY_MS`, longer **AI-to-AI handoff** pause).
- Build preferences and matchup tags load from **`src/main/resources/config/ai-unit-build-profiles.properties`** (`AiUnitBuildHeuristics`) — bias, goodVs, badVs, goodAt tags (LIGHT/MEDIUM/HEAVY, domain, RANGED, CLOAKED, CAPTURE, ANTI_AIR).

---

## Assets and audio

- Terrain PNGs under `src/main/resources/assets/terrain/`: logical name `TerrainType.assetStem()` + `.png`; **`AssetManager` lowercases paths** when loading, so files are typically **`plains_1.png`**, **`sea_main.png`**, etc.
- Structures under `assets/structures/`; units use packed sprites via `AssetManager` / `UnitSpriteMapper`.
- WebP support via TwelveMonkeys `imageio-webp` (Gradle dependency).
- WAV effects referenced by `UnitSoundPaths` / `UnitSoundEffects`.

---

## Multiplayer snapshots (`protocol`)

Authoritative `MatchSnapshot` tiles may include optional **`oreDeposit`** on `TileSnapshot`. **Warmachine** units may carry optional **`warmachineFunds`** on `UnitSnapshot` (if absent on load, the unit keeps its constructor default purse). See `docs/NETWORK_ARCHITECTURE.md` for command coverage.

---

## Related docs

- `docs/STRUCTURE.md` — package / file map.
- `docs/STYLE.md` — UI philosophy aligned with `Theme.java`.
- `docs/NETWORK_ARCHITECTURE.md` — WebSocket + command list.
- `src/main/resources/assets/README.md` — asset folder conventions (updated for current enums).
