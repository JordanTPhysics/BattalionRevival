# Battalion Revival — UI / UX style guide

This guide defines the **intended aesthetic** for the client. **Canonical tokens** for the shipping Swing UI live in **`com.game.ui.Theme`** (`Theme.java`); they were copied from this palette and extended for spacing, borders, and global `UIManager` defaults.

---

## 1. Design vision

The interface follows a **modern tactical / military HUD** look:

- Readable under long sessions; calm contrast.
- Clear hierarchy between **battlefield** and **chrome**.
- Information-dense bottom inspector without mimicking a noisy RTS skin.

Avoid arcade-heavy chrome; favor **function-first** layouts.

---

## 2. Color palette (implemented)

| Purpose | Hex | Java constant |
| ------- | --- | ------------- |
| Root background | `#0F1412` | `Theme.BACKGROUND` |
| Panel surface | `#1A221F` | `Theme.PANEL` |
| Elevated control surface | `#222C28` | `Theme.PANEL_ELEVATED` |
| Hover / pressed fill | `#2A3530` | `Theme.PANEL_HOVER` |
| Hairline border | `#2E3A35` | `Theme.BORDER` |
| Strong selection border | `#3D4D45` | `Theme.BORDER_STRONG` |
| Primary accent | `#4CAF50` | `Theme.ACCENT` |
| Accent hover / secondary green | `#8BC34A` | `Theme.ACCENT_HOVER` |
| Warning | `#FFC107` | `Theme.WARNING` |
| Danger | `#F44336` | `Theme.DANGER` |
| Info | `#03A9F4` | `Theme.INFO` |
| Primary text | `#E0E6E3` | `Theme.TEXT_PRIMARY` |
| Secondary text | `#A5B1AC` | `Theme.TEXT_SECONDARY` |
| Disabled text | `#5F6B66` | `Theme.TEXT_DISABLED` |
| HUD translucent overlay | RGBA `26,34,31,230` | `Theme.HUD_PANEL_TRANSLUCENT` |

**Faction accents** in gameplay pills are **not** fully centralized in `Theme`; `GameWindow.factionAccent` tints Red / Blue / Green / Yellow with bespoke RGB values for instant recognition.

---

## 3. Typography

Swing uses logical **`Font.SANS_SERIF`** everywhere (no bundled font files). Helpers:

| Role | Method |
| ---- | ------ |
| Hero titles | `Theme.fontTitle()` — bold 22 |
| Panel titles | `Theme.fontSubtitle()` — bold 15 |
| Body copy | `Theme.fontBody()` — plain 13 |
| HUD stats | `Theme.fontHud()` / `fontHudBold()` — 13 |
| Micro labels / tooltips | `Theme.fontMicro()` — 11 |
| Buttons | `Theme.fontButton()` — bold 12 |
| Section kicker (uppercase labels) | `Theme.fontSectionLabel()` — bold 10 |

The **start menu** bumps the product wordmark to ~28 pt for branding.

---

## 4. Layout and spacing

**Spacing scale** (`Theme.SPACING_*`): XS `4`, SM `8`, MD `16`, LG `24` px.

**Corner radius token**: `Theme.CORNER_RADIUS = 6` (used conceptually; Swing primitives vary).

**Panels**: Prefer `Theme.panelBorder()` compound (line border + inner padding) for inspector blocks.

**Screen-specific layouts**

- **Game**: Border layout — scrollable map center, **north** mission strip, **south** three-column `GameInfoPanel` inside `MilitaryComponents.titledHudBlock`.
- **Map builder**: **West** fixed-width brush column (~288 px) keeps combo + ghost buttons on-screen; map scroll **center**; **south** status strip with `STATUS` kicker.
- **Factory overlay**: Undecorated dialog sized to parent frame; edge-hover scrolling instead of thick scrollbars (`ViewportEdgePanSupport`).

---

## 5. Components

### Military buttons (`MilitaryButton`)

Primary actions use **accent** fill (`Style.PRIMARY`); destructive menu choices use **danger**; low-friction actions use **ghost** (transparent body, border on hover).

### Pills (`MilitaryComponents.pill`)

Rounded capsule labels for faction state and ONLINE/OFFLINE production lanes—pairs text color with a backing tint.

### HUD blocks

`MilitaryComponents.titledHudBlock(title, inner)` renders uppercase section labels (`Theme.fontSectionLabel`, secondary color) above opaque panel stacks.

### Ability badges (`AbilityPresentation`)

Small rounded squares with **deterministic hue** from ability id hash and **two-letter glyph**. Tooltip copy mirrors gameplay semantics (see `AbilityPresentation.tooltipText`).

---

## 6. Battlefield presentation (`GameWindow.GameMapPanel`)

- **Selection**: Double warm rectangle stroke (`255,245,110` / `255,180,0`) — consistent with map builder cursor.
- **Movement highlight**: Cool translucent overlays for reachable tiles; path drawn as connected segments.
- **Attack preview**: Red-tinted reachable enemy tiles when choosing strikes.
- **Combat**: Short vertical “hop” animation timing coordinated with **staggered** outgoing vs counter SFX so shots do not overlap (`COMBAT_PAUSE_BEFORE_COUNTER_MS`).
- **Factories**: Idle crosshair when the active player may open production (tile empty, factory not yet used this turn).

---

## 7. Global Swing integration

`Theme.installGlobalDefaults()` runs before any window opens (`GameUiLauncher`, `MapBuilderWindow.launch`). It seeds **`UIManager`** entries so **`JOptionPane`**, **`JFileChooser`**, lists, spinners, and tables match the dark palette.

---

## 8. Accessibility

- Maintain **contrast** between `TEXT_PRIMARY` and panel backgrounds.
- **Do not rely on color alone** for critical state: pair faction color with **labels** (“Red”, “Hostile contact”, pill text).
- **Zoom** controls exist in both **game** and **builder**; volume slider offers audio normalization without OS mixer diving.

---

## 9. Sound

**Master gain** via top-bar slider (`ClasspathWavPlayer.setMasterVolume`). Combat/movement sounds respect unit-specific classpath mappings (`UnitSoundPaths`).

---

## 10. Principles

1. **Theme.java first** — new chrome should reuse tokens instead of hard-coded colors.
2. **HUD clarity** — battlefield saturation stays in tiles/sprites; UI stays restrained.
3. **Consistency** — Start menu, builder, and game share `Theme`, `MilitaryButton`, and accent semantics.

This document should stay aligned with **`Theme.java`** and major Swing layouts (`StartMenuWindow`, `GameWindow`, `MapBuilderWindow`, `FactoryBuildDialog`).
