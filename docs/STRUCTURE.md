# Repository structure (source map)

Gradle **Java 17** application (`build.gradle`). Entry point: **`com.game.engine.GameEngine#main`** → loads map → **`GameUiLauncher.launchApplication`**.

Below is the **`src/main/java/com/game/`** tree as implemented (high-signal files only; exploratory stubs omitted). The same packages live in the **`game-core`** Gradle module; **`client`** additionally hosts Swing entrypoints; **`protocol`** holds multiplayer DTOs (`MatchSnapshot`, commands); **`server`** is the authoritative Spring app.

```
com/game/
  audio/
    ClasspathWavPlayer.java      # Master volume; WAV playback from classpath
    GameAudioDiagnostics.java
    SoundLane.java
    UnitSoundEffects.java          # Move / attack / counter SFX routing
    UnitSoundPaths.java            # Classpath paths per unit / event

  engine/
    GameEngine.java                # main(); bootstrap map load + UI launcher (minimal engine loop)
    PlayableGameSession.java       # Full game rules: turns, combat hooks, economy timing, capture,
                                   # factories, Warmachine forge + ore drilling, transports, elimination,
                                   # AI-facing helpers
    TurnManager.java               # Current seat index + round counter; skips eliminated players
    ai/
      AiAction.java
      AiEngine.java                # Seat ownership + action planning
      AiStepRunner.java
      AiTurnExecutor.java          # EDTpaced AI execution vs GameWindow
      AiUnitBuildHeuristics.java   # Reads config/ai-unit-build-profiles.properties

  model/
    Player.java                    # Money, units, structures, eliminated flag, resetTurnState
    Position.java
    map/
      GameMap.java                 # Grid 10–40, teamCount 2–4, resize preserving overlap
      TerrainType.java             # Large texture catalog + mobility + defense modifiers
      Tile.java                    # Terrain, structure, runtime Unit, editor sprite/facing/teams,
                                   # ore-deposit overlay (Warmachine drilling)
    structures/
      Structure.java               # Owner, capture progress, income switch
      StructureType.java         # Capital, Factory, controls, oil variants
    units/
      EngagementRules.java        # Air, sub, cloak targeting gates
      FacingDirection.java
      Unit.java                    # Stats, action flag, cloak state, transport morph fields,
                                   # Warmachine onboard fabrication purse
      UnitType.java                # Enum roster + factory categories + transport helpers

  pathfinding/
    AStar.java
    GridPathfinder.java
    UnitMovementPaths.java         # Validates paths vs terrain + jamming + discovery stops

  systems/
    CombatSystem.java              # Damage, counters, piercing, stalwart roll, HP effectiveness bands
    CombatTerrain.java             # Ranged hill +1 max range
    EconomySystem.java             # Per-structure income application
    CaptureSystem.java             # (present; capture progression primarily on Structure + session)
    FactorySpawn.java              # Factory BFS spawn + orthogonal adjacent spawn for Warmachine
    JammingRules.java
    MovementReach.java
    MovementSystem.java

  persistence/
    MapJsonPersistence.java        # maps/*.json load/save; tile oreDeposit optional field

  ui/
    AbilityPresentation.java       # Ability HUD glyphs + tooltip strings
    AssetManager.java              # Terrain / structure / unit frames, tinting, drawing helpers
    ExplosionEffect.java
    FactoryBuildDialog.java        # Fullscreen production UI (land/sea/air categories)
    WarmachineBuildDialog.java     # Warmachine fabrication (war purse; no control-structure gates)
    GameUiLauncher.java            # Theme install + StartMenuWindow
    GameWindow.java                # Play view: map panel, HUD, AI glue, selection / combat animation,
                                   # Warmachine context menu (fabricate / drill), ore-marker paint
    MapBuilderPanel.java           # Editor canvas + brushes + zoom
    MapBuilderWindow.java          # Editor chrome + JSON save/load + toolbar
    MapSelectionDialog.java        # Pick JSON from maps/
    MilitaryButton.java
    MilitaryComponents.java        # Title labels, pills, HUD titled blocks
    SpritePrimaryRecolor.java
    StartMenuWindow.java           # Main menu + navigation to skirmish / builder
    TextureOption.java             # Combo wrapper for icon + value (builder)
    Theme.java                     # Colors, fonts, spacing, UIManager defaults
    UiStub.java                    # Legacy / placeholder hook (minimal)
    ViewportEdgePanSupport.java    # Scroll viewport edge panning for map surfaces
```

## Resources (runtime)

```
src/main/resources/
  assets/
    terrain/           # PNG/WebP per TerrainType.assetStem()
    structures/
    units/             # Sheet-backed sprites (ids align with editor / UnitSpriteMapper)
  config/
    ai-unit-build-profiles.properties
```

## Maps on disk

- **`maps/`** at working directory: JSON maps, optional **`default.json`** for `GameEngine` auto-load.

## Tests

- **`game-core/src/test/java`** — JUnit 5 (`build.gradle`); rules tests live with simulation code.

- **`docs/NETWORK_ARCHITECTURE.md`** — Multiplayer WebSocket flow + protocol alignment.
- **`docs/STYLE.md`** — Visual / UX guidelines + `Theme` linkage.
- **`docs/STRUCTURE.md`** — This file.
