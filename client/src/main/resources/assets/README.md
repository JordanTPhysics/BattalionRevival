# Asset folders

Runtime artwork lives under `src/main/resources/assets/` (classpath root **`/assets/`**).

## Terrain (`assets/terrain/`)

Each **`TerrainType`** constant has an **`assetStem()`** (e.g. `Plains_1`, `Sea_Main`). **`AssetManager`** loads **`/assets/terrain/<stem>.png`** but **normalizes the path to lowercase** for classpath lookup, so the shipped files should be named in **lowercase** (e.g. `plains_1.png`, `sea_main.png`).

Place **PNG** (or other formats supported by ImageIO + plugins) using that pattern. If a file is missing, the client paints **`TerrainType.fallbackMapColor()`** as a flat fill (map builder and game).

The older five-name list (`plains.png`, `forest.png`, …) is **obsolete**; the game ships a **large autotile-style set** aligned with the enum.

## Structures (`assets/structures/`)

Filenames are **`<StructureType enum>.png` in lowercase** (see `AssetManager.loadStructureImages`), e.g.:

| StructureType   | File on disk    |
| --------------- | --------------- |
| AirControl      | `aircontrol.png` |
| Capital         | `capital.png` |
| Factory         | `factory.png` |
| GroundControl   | `groundcontrol.png` |
| OilAdvanced     | `oiladvanced.png` |
| OilRefinery     | `oilrefinery.png` |
| OilRig          | `oilrig.png` |
| SeaControl      | `seacontrol.png` |

Neutral structures may be **gray-recolored** in the map builder (`AssetManager.STRUCTURE_NEUTRAL_RECOLOR`); factions **2–4** tint toward blue/green/yellow.

## Units (`assets/units/`)

Unit sprites are **sheet-based** (multiple facings / frames). IDs referenced from map JSON **`unitSprite`** strings and internally mapped to **`UnitType`** via **`UnitSpriteMapper`**.

Use the map builder **Unit Brush** combo (driven by `AssetManager.getAvailableUnitSpriteIds()`) to see which sprite ids are discovered on the classpath.

---

See **`docs/PROJECT.md`** for how tiles reference terrain and structures in JSON maps.
