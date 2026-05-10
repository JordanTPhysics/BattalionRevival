"use client";

import { useEffect, useRef } from "react";
import {
  Application,
  Assets,
  Container,
  FederatedPointerEvent,
  Graphics,
  Sprite,
  Texture,
} from "pixi.js";
import { EDGE_PADDING_TILES } from "@/lib/editor/catalog";
import { eastFrameTexture } from "@/lib/editor/unitEastFrame";
import {
  structureTextureUrl,
  terrainTextureUrl,
  uncolouredStructureTextureUrl,
  uncolouredUnitTextureUrl,
} from "@/lib/game/renderPaths";
import {
  getUncolouredEastFrameTeamTexture,
  getUncolouredFullImageTeamTexture,
} from "@/lib/game/uncolouredTextures";
import { useEditorStore } from "@/stores/editorStore";

const PANEL_BG = 0x222831;
const MAP_BG = 0x1c222a;
/** If RMB moves less than this (screen px) before release, treat as click-to-erase; otherwise pan. */
const RMB_PAN_THRESHOLD_PX = 8;

const texturePool = new Map<string, Texture>();

async function pooledTexture(url: string, fallback: () => Texture): Promise<Texture> {
  const hit = texturePool.get(url);
  if (hit) return hit;
  try {
    const t = await Assets.load<Texture>(url);
    texturePool.set(url, t);
    return t;
  } catch {
    const t = fallback();
    texturePool.set(url, t);
    return t;
  }
}

function solidTexture(hex: number, w = 16, h = 16): Texture {
  const c = document.createElement("canvas");
  c.width = w;
  c.height = h;
  const ctx = c.getContext("2d");
  if (ctx) {
    ctx.fillStyle = `#${hex.toString(16).padStart(6, "0")}`;
    ctx.fillRect(0, 0, w, h);
  }
  return Texture.from(c);
}

function fallbackTerrainRgb(terrain: string): number {
  if (terrain.includes("SEA")) return 0x4186c9;
  if (terrain.includes("REEF")) return 0x58a8c6;
  if (terrain.includes("SHORE") || terrain.includes("ARCHIPELAGO")) return 0x828282;
  if (terrain.includes("CANYON") || terrain.includes("MOUNTAINS") || terrain.includes("ROCK")) {
    return 0x6e6e6e;
  }
  if (terrain.includes("FOREST") || terrain.includes("HILLS")) return 0x397a37;
  if (terrain.includes("BRIDGE")) return 0x4186c9;
  if (terrain.includes("HIGH_BRIDGE")) return 0x58a8c6;
  return 0x748c56;
}

function teamRgb(teamId: number | null): number | null {
  if (teamId == null || teamId <= 0) return null;
  switch (teamId) {
    case 1:
      return 0xae0000; //0x59009b;
    case 2:
      return 0x468ceb;
    case 3:
      return 0x50be5f;
    case 4:
      return 0xebbf41;
    default:
      return 0x59009b;
  }
}

function structureTintRgb(structureTeamId: number | null): number {
  if (structureTeamId == null) return 0xb4b4bc;
  if (structureTeamId <= 0) return 0xffffff;
  return teamRgb(structureTeamId) ?? 0xffffff;
}

/** RGB for unit mask fill / legacy tint — team 1 uses palette (uncoloured sprites, not red base). */
function spriteTintUnit(teamId: number | null): number {
  if (teamId == null || teamId <= 0) return 0xffffff;
  return teamRgb(teamId) ?? 0xffffff;
}

function texNaturalW(t: Texture): number {
  const o = "orig" in t ? (t.orig as { width: number }) : null;
  return Math.max(1, o?.width ?? t.width);
}

function texNaturalH(t: Texture): number {
  const o = "orig" in t ? (t.orig as { height: number }) : null;
  return Math.max(1, o?.height ?? t.height);
}

export interface EditorBoardCanvasProps {
  className?: string;
}

export function EditorBoardCanvas({ className }: EditorBoardCanvasProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const panRootRef = useRef<Container | null>(null);
  const appReadyRef = useRef(false);

  const spaceHeld = useRef(false);
  const panDragging = useRef(false);
  const paintDragging = useRef(false);
  const lastPan = useRef<{ x: number; y: number } | null>(null);
  const lastPaintCell = useRef<{ x: number; y: number } | null>(null);

  const rmbDown = useRef(false);
  const rmbPanLatched = useRef(false);
  const rmbPressStartGlobal = useRef<{ x: number; y: number } | null>(null);
  const rmbLastGlobal = useRef<{ x: number; y: number } | null>(null);
  const rmbPressGrid = useRef<{ x: number; y: number } | null>(null);

  const revision = useEditorStore((s) => s.revision);
  const pan = useEditorStore((s) => s.pan);
  const tileSize = useEditorStore((s) => s.tileSize);
  const mapSnap = useEditorStore((s) => s.map);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const hostEl = hostRef.current;
      if (hostEl && document.activeElement === hostEl && e.code === "Space") {
        e.preventDefault();
        spaceHeld.current = true;
      } else if (e.code === "Space") {
        spaceHeld.current = true;
      }
      const step = 32;
      if (e.code === "ArrowLeft") useEditorStore.getState().panBy(step, 0);
      if (e.code === "ArrowRight") useEditorStore.getState().panBy(-step, 0);
      if (e.code === "ArrowUp") useEditorStore.getState().panBy(0, step);
      if (e.code === "ArrowDown") useEditorStore.getState().panBy(0, -step);
    };
    const onKeyUp = (e: KeyboardEvent) => {
      const hostEl = hostRef.current;
      if (hostEl && document.activeElement === hostEl && e.code === "Space") {
        e.preventDefault();
      }
      if (e.code === "Space") spaceHeld.current = false;
    };
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
    };
  }, []);

  useEffect(() => {
    const panRoot = panRootRef.current;
    if (!panRoot || !appReadyRef.current) return;
    panRoot.position.set(pan.x, pan.y);
  }, [pan.x, pan.y]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;

    const app = new Application();
    const panRoot = new Container();
    panRootRef.current = panRoot;

    let destroyed = false;

    const wheel = (ev: WheelEvent) => {
      if (!host.contains(ev.target as Node)) return;
      ev.preventDefault();
      if (ev.deltaY > 0) useEditorStore.getState().zoomOut();
      else useEditorStore.getState().zoomIn();
    };
    host.addEventListener("wheel", wheel, { passive: false });

    const toGrid = (
      panContainer: Container,
      e: FederatedPointerEvent
    ): { x: number; y: number } | null => {
      const st = useEditorStore.getState();
      const ts = st.tileSize;
      const map = st.map;
      const local = panContainer.toLocal(e.global);
      const pad = EDGE_PADDING_TILES * ts;
      const gx = Math.floor((local.x - pad) / ts);
      const gy = Math.floor((local.y - pad) / ts);
      if (gx < 0 || gy < 0 || gx >= map.width || gy >= map.height) return null;
      return { x: gx, y: gy };
    };

    const onDown = (e: FederatedPointerEvent) => {
      host.focus({ preventScroll: true });
      if (spaceHeld.current && e.button === 0) {
        panDragging.current = true;
        lastPan.current = { x: e.global.x, y: e.global.y };
        return;
      }

      paintDragging.current = false;
      lastPaintCell.current = null;

      if (e.button === 2) {
        rmbDown.current = true;
        rmbPanLatched.current = false;
        rmbPressStartGlobal.current = { x: e.global.x, y: e.global.y };
        rmbLastGlobal.current = { x: e.global.x, y: e.global.y };
        rmbPressGrid.current = toGrid(panRoot, e);
        return;
      }

      if (e.shiftKey) {
        const cell = toGrid(panRoot, e);
        if (!cell) return;
        lastPaintCell.current = cell;
        useEditorStore.getState().applyBrushAt(cell.x, cell.y, "erase");
        return;
      }

      if (e.button !== 0) return;
      paintDragging.current = true;
      const cell = toGrid(panRoot, e);
      if (!cell) return;
      lastPaintCell.current = cell;
      const store = useEditorStore.getState();
      if (store.oreDepositBrushMode) {
        store.applyBrushAt(cell.x, cell.y, "ore_toggle");
      } else {
        store.applyBrushAt(cell.x, cell.y, "paint");
      }
    };

    const clearRmbGesture = () => {
      rmbDown.current = false;
      rmbPanLatched.current = false;
      rmbPressStartGlobal.current = null;
      rmbLastGlobal.current = null;
      rmbPressGrid.current = null;
    };

    const onUp = (e: FederatedPointerEvent) => {
      panDragging.current = false;
      paintDragging.current = false;
      lastPan.current = null;
      lastPaintCell.current = null;

      if (rmbDown.current && e.button === 2) {
        if (!rmbPanLatched.current) {
          const cell = rmbPressGrid.current;
          if (cell) useEditorStore.getState().applyBrushAt(cell.x, cell.y, "erase");
        }
        clearRmbGesture();
      }
    };

    const onCancel = () => {
      panDragging.current = false;
      paintDragging.current = false;
      lastPan.current = null;
      lastPaintCell.current = null;
      if (rmbDown.current) clearRmbGesture();
    };

    const onMove = (e: FederatedPointerEvent) => {
      if (panDragging.current && lastPan.current) {
        const dx = e.global.x - lastPan.current.x;
        const dy = e.global.y - lastPan.current.y;
        lastPan.current = { x: e.global.x, y: e.global.y };
        useEditorStore.getState().panBy(dx, dy);
        return;
      }

      if (rmbDown.current && (e.buttons & 2) !== 0) {
        const start = rmbPressStartGlobal.current;
        const lastG = rmbLastGlobal.current;
        if (!start || !lastG) return;
        const dist = Math.hypot(e.global.x - start.x, e.global.y - start.y);
        if (!rmbPanLatched.current && dist >= RMB_PAN_THRESHOLD_PX) rmbPanLatched.current = true;
        if (rmbPanLatched.current) {
          const dx = e.global.x - lastG.x;
          const dy = e.global.y - lastG.y;
          rmbLastGlobal.current = { x: e.global.x, y: e.global.y };
          useEditorStore.getState().panBy(dx, dy);
        }
        return;
      }

      if ((e.buttons & 1) !== 0 && e.shiftKey) {
        const cell = toGrid(panRoot, e);
        if (!cell) return;
        lastPaintCell.current = cell;
        useEditorStore.getState().applyBrushAt(cell.x, cell.y, "erase");
        return;
      }

      if (!paintDragging.current) return;
      const cell = toGrid(panRoot, e);
      if (!cell) return;

      const store = useEditorStore.getState();
      if (store.oreDepositBrushMode) {
        lastPaintCell.current = cell;
        store.applyBrushAt(cell.x, cell.y, "ore_toggle");
        return;
      }

      const last = lastPaintCell.current;
      if (last && last.x === cell.x && last.y === cell.y) return;
      lastPaintCell.current = cell;
      store.applyBrushAt(cell.x, cell.y, "paint");
    };

    /** Runs only after Pixi Application.init() — avoids destroying an app still initializing (Strict Mode / fast navigate). */
    let teardown: (() => void) | null = null;

    void app
      .init({
        background: PANEL_BG,
        antialias: true,
        resizeTo: host,
      })
      .then(() => {
        if (destroyed) {
          app.destroy(true, { children: true, texture: false });
          return;
        }
        teardown = () => {
          try {
            app.stage.off("pointerdown", onDown);
            app.stage.off("pointerup", onUp);
            app.stage.off("pointerupoutside", onUp);
            app.stage.off("pointercancel", onCancel);
            app.stage.off("pointermove", onMove);
          } finally {
            app.destroy(true, { children: true, texture: false });
          }
        };

        host.appendChild(app.canvas);
        app.stage.addChild(panRoot);
        app.stage.eventMode = "static";
        app.stage.hitArea = app.screen;

        app.stage.on("pointerdown", onDown);
        app.stage.on("pointerup", onUp);
        app.stage.on("pointerupoutside", onUp);
        app.stage.on("pointercancel", onCancel);
        app.stage.on("pointermove", onMove);

        appReadyRef.current = true;
        panRoot.position.set(useEditorStore.getState().pan.x, useEditorStore.getState().pan.y);
      })
      .catch(() => {
        /* init failed; nothing to tear down */
      });

    return () => {
      destroyed = true;
      appReadyRef.current = false;
      panRootRef.current = null;
      host.removeEventListener("wheel", wheel);
      if (teardown) {
        teardown();
        teardown = null;
      }
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    const panRoot = panRootRef.current;
    if (!panRoot || !appReadyRef.current) return () => {};

    const snap = mapSnap;

    panRoot.position.set(useEditorStore.getState().pan.x, useEditorStore.getState().pan.y);

    void (async () => {
      panRoot.removeChildren();

      const board = new Container();
      panRoot.addChild(board);

      const map = snap;
      const ts = tileSize;

      const totalW = (map.width + 2 * EDGE_PADDING_TILES) * ts;
      const totalH = (map.height + 2 * EDGE_PADDING_TILES) * ts;

      const chrome = new Graphics();
      chrome.rect(0, 0, totalW, totalH);
      chrome.fill(PANEL_BG);

      chrome.rect(
        EDGE_PADDING_TILES * ts,
        EDGE_PADDING_TILES * ts,
        map.width * ts,
        map.height * ts
      );
      chrome.fill(MAP_BG);
      board.addChild(chrome);

      const origin = EDGE_PADDING_TILES * ts;

      for (let y = 0; y < map.height; y++) {
        for (let x = 0; x < map.width; x++) {
          if (cancelled) return;
          const tile = map.tiles[y][x];
          const px = origin + x * ts;
          const py = origin + y * ts;

          const tUrl = terrainTextureUrl(tile.terrain);
          const fb = fallbackTerrainRgb(tile.terrain);
          const tTex = await pooledTexture(tUrl, () => solidTexture(fb, ts, ts));

          const iw = texNaturalW(tTex);
          const ih = texNaturalH(tTex);
          const drawH = Math.max(1, Math.round((ih * ts) / iw));

          if (drawH < ts) {
            const filler = new Sprite(solidTexture(fb, ts, ts - drawH));
            filler.width = ts;
            filler.height = ts - drawH;
            filler.x = px;
            filler.y = py;
            board.addChild(filler);
          }

          const tr = new Sprite(tTex);
          tr.width = ts;
          tr.height = drawH;
          tr.x = px;
          tr.y = py + ts - drawH;
          board.addChild(tr);

          const edge = new Graphics();
          edge.rect(px, py, ts, ts);
          edge.stroke({ width: 1, color: 0x000000, alpha: 0.27 });
          board.addChild(edge);

          if (tile.structure != null) {
            const structureFillRgb = structureTintRgb(tile.structureTeam);
            const uncStUrl = uncolouredStructureTextureUrl(tile.structure);
            const maskedSt = await getUncolouredFullImageTeamTexture(uncStUrl, structureFillRgb);
            let useLegacyStructureTint = false;
            let stTex: Texture;
            if (maskedSt) {
              stTex = maskedSt;
            } else {
              useLegacyStructureTint = true;
              const sUrl = structureTextureUrl(tile.structure);
              if (sUrl) {
                stTex = await pooledTexture(sUrl, () => solidTexture(0xffffff, ts, ts));
              } else {
                stTex = solidTexture(0xffffff, ts, ts);
              }
            }
            const inset = Math.max(2, Math.round(ts / 8));
            const ss = new Sprite(stTex);
            ss.width = ts - inset * 2;
            ss.height = ts - inset * 2;
            ss.x = px + inset;
            ss.y = py + inset;
            ss.tint = useLegacyStructureTint ? structureFillRgb : 0xffffff;

            board.addChild(ss);

            const rBadge = Math.max(5, Math.round(ts / 5));
            const cx = px + ts - rBadge * 0.5 - 2;
            const cy = py + rBadge * 0.5 + 2;
            const bgStr = new Graphics();
            bgStr.circle(cx, cy, rBadge * 0.5);
            if (tile.structureTeam == null) {
              bgStr.fill(0x6e6e76);
              bgStr.stroke({ width: 1, color: 0x000000, alpha: 0.55 });
            } else {
              bgStr.fill(teamRgb(tile.structureTeam) ?? 0xffffff);
              bgStr.stroke({ width: 1, color: 0x000000, alpha: 0.55 });
            }
            board.addChild(bgStr);
          }

          if (tile.unitSprite != null) {
            const unitFillRgb = spriteTintUnit(tile.unitTeam);
            const uncUrl = uncolouredUnitTextureUrl(tile.unitSprite);
            const maskedUnit = await getUncolouredEastFrameTeamTexture(uncUrl, unitFillRgb);
            let useLegacyUnitTint = false;
            let uTex: Texture;
            if (maskedUnit) {
              uTex = maskedUnit;
            } else {
              useLegacyUnitTint = true;
              const legacyUrl = `/assets/units/${tile.unitSprite}.png`;
              const primary = await eastFrameTexture(legacyUrl);
              uTex =
                primary ?? (await pooledTexture(legacyUrl, () => solidTexture(0xcccccc)));
            }

            const rawW = texNaturalW(uTex);
            const rawH = texNaturalH(uTex);
            const uw = ts;
            const uh = Math.max(1, Math.round((rawH * ts) / rawW));

            const us = new Sprite(uTex);
            us.width = uw;
            us.height = uh;
            us.anchor.set(0.5, 0.75);
            us.x = px + ts * 0.5;
            us.y = py + ts * 0.5;
            us.tint = useLegacyUnitTint ? unitFillRgb : 0xffffff;
            board.addChild(us);

            if (tile.unitTeam != null && tile.unitTeam > 0) {
              const rBadge = Math.max(5, Math.round(ts / 5));
              const cx = px + ts - rBadge * 0.5 - 2;
              const cy = py + rBadge * 0.5 + 2;
              const bub = new Graphics();
              bub.circle(cx, cy, rBadge * 0.5);
              bub.fill(teamRgb(tile.unitTeam) ?? 0xffffff);
              bub.stroke({ width: 1, color: 0x000000, alpha: 0.55 });
              board.addChild(bub);
            }
          }

          if (tile.oreDeposit) {
            const rOre = Math.max(4, Math.round(ts / 6));
            const ox = px + ts - rOre - 3;
            const oy = py + ts - rOre - 3;
            const og = new Graphics();
            og.ellipse(
              ox + rOre * 0.5,
              oy + rOre * 0.5,
              Math.max(rOre * 0.45, 1),
              Math.max(rOre * 0.45, 1)
            );
            og.fill(0xffdc3c);
            og.stroke({ width: 1, color: 0x5a3c0a, alpha: 0.79 });
            board.addChild(og);
          }
        }
      }

      if (cancelled) return;

      const sel = useEditorStore.getState().selectedCell;
      if (sel && sel.x >= 0 && sel.y >= 0) {
        const px = origin + sel.x * ts;
        const py = origin + sel.y * ts;
        const g1 = new Graphics();
        g1.rect(px + 1, py + 1, ts - 2, ts - 2);
        g1.stroke({ width: 1, color: 0xfff56e, alpha: 0.86 });

        const g2 = new Graphics();
        g2.rect(px + 2, py + 2, ts - 4, ts - 4);
        g2.stroke({ width: 1, color: 0xffb400, alpha: 0.86 });
        board.addChild(g1);
        board.addChild(g2);
      }

      panRoot.position.set(useEditorStore.getState().pan.x, useEditorStore.getState().pan.y);
    })();

    return () => {
      cancelled = true;
    };
  }, [revision, tileSize, mapSnap]);

  return (
    <div
      ref={hostRef}
      tabIndex={0}
      role="application"
      aria-label="Map editor canvas"
      className={`relative flex min-h-[520px] min-w-0 flex-1 outline-none ring-1 ring-zinc-800 ${className ?? ""}`}
      onContextMenu={(e) => e.preventDefault()}
    />
  );
}
