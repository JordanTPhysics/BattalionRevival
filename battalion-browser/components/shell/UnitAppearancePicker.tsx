"use client";

import { useEffect, useId, useRef, useState } from "react";
import { DEFAULT_PLAYER_UNIT_RGB, TeamPaintStyle } from "@/lib/game/teamPaintStyle";
import { usePlayerUnitAppearanceStore } from "@/stores/playerUnitAppearanceStore";

function rgbToHex(rgb: number): string {
  return `#${(rgb & 0xffffff).toString(16).padStart(6, "0")}`;
}

function hexToRgb(hex: string): number {
  const h = hex.replace("#", "").trim();
  if (h.length === 3) {
    return parseInt(
      h
        .split("")
        .map((c) => c + c)
        .join(""),
      16
    );
  }
  const n = parseInt(h, 16);
  return Number.isFinite(n) ? n & 0xffffff : DEFAULT_PLAYER_UNIT_RGB;
}

function previewBackground(style: TeamPaintStyle): string {
  if (style.kind === "solid") {
    return rgbToHex(style.rgb);
  }
  return `linear-gradient(${style.angleDeg}deg, ${rgbToHex(style.from)}, ${rgbToHex(style.to)})`;
}

export function UnitAppearancePicker() {
  const panelId = useId();
  const paintStyle = usePlayerUnitAppearanceStore((s) => s.paintStyle);
  const setPaintStyle = usePlayerUnitAppearanceStore((s) => s.setPaintStyle);
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      const el = rootRef.current;
      if (el && !el.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const mode: "solid" | "linear" = paintStyle.kind === "linear" ? "linear" : "solid";
  const solidRgb = paintStyle.kind === "solid" ? paintStyle.rgb : DEFAULT_PLAYER_UNIT_RGB;
  const gradFrom = paintStyle.kind === "linear" ? paintStyle.from : 0x4682d2;
  const gradTo = paintStyle.kind === "linear" ? paintStyle.to : 0xc84646;
  const gradAngle = paintStyle.kind === "linear" ? paintStyle.angleDeg : 125;

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((v) => !v)}
        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-zinc-600 bg-zinc-900/80 shadow-inner ring-zinc-500 transition hover:border-zinc-500 hover:ring-1"
        title="My unit colours"
      >
        <span
          className="h-6 w-6 rounded-sm border border-black/40"
          style={{ background: previewBackground(paintStyle) }}
        />
      </button>
      {open ? (
        <div
          id={panelId}
          role="dialog"
          aria-label="Unit appearance"
          className="absolute right-0 z-[60] mt-2 w-[min(18rem,calc(100vw-2rem))] rounded-lg border border-zinc-700 bg-zinc-900/95 p-3 shadow-xl backdrop-blur"
        >
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-zinc-500">
            Your units (this device)
          </p>
          <div className="mb-3 flex gap-2">
            <label className="flex cursor-pointer items-center gap-1.5 text-xs text-zinc-300">
              <input
                type="radio"
                name="unitPaintMode"
                checked={mode === "solid"}
                onChange={() => setPaintStyle({ kind: "solid", rgb: solidRgb })}
              />
              Solid
            </label>
            <label className="flex cursor-pointer items-center gap-1.5 text-xs text-zinc-300">
              <input
                type="radio"
                name="unitPaintMode"
                checked={mode === "linear"}
                onChange={() =>
                  setPaintStyle({
                    kind: "linear",
                    angleDeg: gradAngle,
                    from: gradFrom,
                    to: gradTo,
                  })
                }
              />
              Gradient
            </label>
          </div>

          {mode === "solid" ? (
            <label className="flex items-center gap-2 text-sm text-zinc-200">
              <span className="text-zinc-500">Colour</span>
              <input
                type="color"
                value={rgbToHex(solidRgb)}
                onChange={(e) => setPaintStyle({ kind: "solid", rgb: hexToRgb(e.target.value) })}
                className="h-9 w-14 cursor-pointer rounded border border-zinc-600 bg-zinc-950 p-0.5"
              />
            </label>
          ) : (
            <div className="flex flex-col gap-3">
              <label className="flex items-center gap-2 text-sm text-zinc-200">
                <span className="w-12 shrink-0 text-zinc-500">From</span>
                <input
                  type="color"
                  value={rgbToHex(gradFrom)}
                  onChange={(e) =>
                    setPaintStyle({
                      kind: "linear",
                      angleDeg: gradAngle,
                      from: hexToRgb(e.target.value),
                      to: gradTo,
                    })
                  }
                  className="h-9 w-14 cursor-pointer rounded border border-zinc-600 bg-zinc-950 p-0.5"
                />
              </label>
              <label className="flex items-center gap-2 text-sm text-zinc-200">
                <span className="w-12 shrink-0 text-zinc-500">To</span>
                <input
                  type="color"
                  value={rgbToHex(gradTo)}
                  onChange={(e) =>
                    setPaintStyle({
                      kind: "linear",
                      angleDeg: gradAngle,
                      from: gradFrom,
                      to: hexToRgb(e.target.value),
                    })
                  }
                  className="h-9 w-14 cursor-pointer rounded border border-zinc-600 bg-zinc-950 p-0.5"
                />
              </label>
              <label className="flex flex-col gap-1 text-sm text-zinc-200">
                <span className="text-zinc-500">Angle ({Math.round(gradAngle)}°)</span>
                <input
                  type="range"
                  min={0}
                  max={360}
                  step={1}
                  value={gradAngle}
                  onChange={(e) =>
                    setPaintStyle({
                      kind: "linear",
                      angleDeg: Number(e.target.value),
                      from: gradFrom,
                      to: gradTo,
                    })
                  }
                  className="w-full accent-emerald-500"
                />
              </label>
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}
