"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { EditorBoardCanvas } from "@/components/editor/EditorBoardCanvas";
import {
  STRUCTURE_TYPES,
  TERRAIN_TYPES,
  UNIT_SPRITE_IDS,
  BASE_TILE_SIZE,
} from "@/lib/editor/catalog";
import { slugFromStem, sanitizeMapFileStem, isValidUploadSlug } from "@/lib/maps/mapNames";
import { parseMapJson, serializeMapJson } from "@/lib/maps/mapJson";
import { defaultGameServerOrigin } from "@/lib/network/matchClient";
import { uploadMapToServer } from "@/lib/network/mapCatalogClient";
import { useEditorStore } from "@/stores/editorStore";

function terrainLabel(enumName: string): string {
  return enumName.toLowerCase().replaceAll("_", " ");
}

/** Remount grid inputs when authoritative map dims or stem change (Swing spinners ↔ map sync). */
function GridCornerControls() {
  const map = useEditorStore((s) => s.map);
  const applyGridSize = useEditorStore((s) => s.applyGridSize);
  const zoomIn = useEditorStore((s) => s.zoomIn);
  const zoomOut = useEditorStore((s) => s.zoomOut);
  const zoomPct = useEditorStore((s) =>
    Math.round((s.tileSize * 100) / BASE_TILE_SIZE)
  );

  const [wSpec, setWSpec] = useState(String(map.width));
  const [hSpec, setHSpec] = useState(String(map.height));

  return (
    <>
      <span className="text-[10px] font-bold uppercase text-[#a5b1ac]">
        Grid
      </span>
      <input
        className="h-9 w-14 rounded border border-[#2e3a35] bg-[#222c28] px-2 text-[13px] text-[#e0e6e3]"
        type="number"
        min={10}
        max={40}
        value={wSpec}
        onChange={(e) => setWSpec(e.target.value)}
      />
      <span className="text-[#e0e6e3]">×</span>
      <input
        className="h-9 w-14 rounded border border-[#2e3a35] bg-[#222c28] px-2 text-[13px] text-[#e0e6e3]"
        type="number"
        min={10}
        max={40}
        value={hSpec}
        onChange={(e) => setHSpec(e.target.value)}
      />
      <GhostPadButton
        type="button"
        onClick={() => {
          const w = Number.parseInt(wSpec, 10);
          const h = Number.parseInt(hSpec, 10);
          applyGridSize(w, h);
          const snap = useEditorStore.getState().map;
          setWSpec(String(snap.width));
          setHSpec(String(snap.height));
        }}
      >
        Apply
      </GhostPadButton>

      <span className="ml-4 text-[10px] font-bold uppercase text-[#a5b1ac]">
        Zoom
      </span>
      <GhostPadButton type="button" aria-label="Zoom out" onClick={zoomOut}>
        −
      </GhostPadButton>
      <span className="w-11 text-center tabular-nums text-sm">{zoomPct}%</span>
      <GhostPadButton type="button" aria-label="Zoom in" onClick={zoomIn}>
        +
      </GhostPadButton>
    </>
  );
}

export default function EditorPage() {
  const fileRef = useRef<HTMLInputElement>(null);

  const map = useEditorStore((s) => s.map);
  const statusLine = useEditorStore((s) => s.statusLine);

  const selectedTerrain = useEditorStore((s) => s.selectedTerrain);
  const selectedStructure = useEditorStore((s) => s.selectedStructure);
  const selectedUnit = useEditorStore((s) => s.selectedUnitSprite);
  const brushTeamId = useEditorStore((s) => s.brushTeamId);
  const oreMode = useEditorStore((s) => s.oreDepositBrushMode);
  const mapName = useEditorStore((s) => s.mapName);

  const setTerrain = useEditorStore((s) => s.setSelectedTerrain);
  const setStructure = useEditorStore((s) => s.setSelectedStructure);
  const setUnit = useEditorStore((s) => s.setSelectedUnitSprite);
  const setBrush = useEditorStore((s) => s.setBrushTeamId);
  const setOre = useEditorStore((s) => s.setOreDepositBrushMode);
  const setMapNameField = useEditorStore((s) => s.setMapName);

  const setTeamCount = useEditorStore((s) => s.setTeamCount);

  const fillTerrain = useEditorStore((s) => s.fillTerrainEverywhere);
  const clearStructs = useEditorStore((s) => s.clearAllStructures);
  const clearUnits = useEditorStore((s) => s.clearAllUnits);
  const resetPlain = useEditorStore((s) => s.resetToPlainsDestructive);
  const resetTerrainCombo = useEditorStore((s) => s.resetTerrainComboToDefault);
  const replaceMap = useEditorStore((s) => s.replaceMapFromSnapshot);

  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadOrigin, setUploadOrigin] = useState(defaultGameServerOrigin());
  const [uploadSlug, setUploadSlug] = useState("my-map");
  const [uploadOwner, setUploadOwner] = useState("anonymous");
  const [uploadBusy, setUploadBusy] = useState(false);

  const saveDownload = () => {
    const raw = mapName.trim();
    if (!raw) {
      useEditorStore.getState().setStatus("Please enter a map name before saving.");
      return;
    }
    const stem = sanitizeMapFileStem(raw);
    if (!stem) {
      useEditorStore
        .getState()
        .setStatus(
          "Map name is not usable as a file name. Use letters, numbers, or spaces."
        );
      return;
    }
    const blob = new Blob([serializeMapJson(map)], {
      type: "application/json;charset=utf-8",
    });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = stem + ".json";
    a.click();
    URL.revokeObjectURL(a.href);
    useEditorStore.getState().setStatus(`Saved ${stem}.json`);
  };

  const loadJsonFile = async (file: File) => {
    const text = await file.text();
    const loaded = parseMapJson(text);
    replaceMap(loaded);
    const name =
      file.name.toLowerCase().endsWith(".json") ? file.name.slice(0, -5) : file.name;
    setMapNameField(name);
    useEditorStore.getState().setStatus(`Loaded map from ${file.name}`);
  };

  const runUpload = async () => {
    const slug = uploadSlug.trim().toLowerCase();
    if (!isValidUploadSlug(slug)) {
      useEditorStore
        .getState()
        .setStatus(
          "Invalid slug: 2–63 chars; start letter/digit; then letters, digits, or hyphens only."
        );
      return;
    }
    setUploadBusy(true);
    try {
      const body = serializeMapJson(map);
      const msg = await uploadMapToServer(
        uploadOrigin,
        slug,
        uploadOwner,
        body
      );
      useEditorStore.getState().setStatus(`Upload: ${msg}`);
      setUploadOpen(false);
    } catch (e) {
      const err = e instanceof Error ? e.message : String(e);
      useEditorStore.getState().setStatus(`Upload failed: ${err}`);
    } finally {
      setUploadBusy(false);
    }
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-0 lg:flex-row lg:min-h-[calc(100vh-8rem)]">
      <aside className="w-full shrink-0 border-r border-[#2e3a35] bg-[#1a221f] p-4 lg:w-[288px] lg:overflow-y-auto">
        <p className="text-[11px] font-bold uppercase tracking-wide text-[#4caf50]">
          MAP BUILDER
        </p>
        <p className="mt-1 text-[15px] font-semibold text-[#e0e6e3]">
          Tactical Field Editor
        </p>
        <div className="my-4 h-px w-full bg-[#2e3a35]" />

        <ToolbarSectionTitle>Terrain brush</ToolbarSectionTitle>
        <BrushRow>
          <select
            value={selectedTerrain}
            onChange={(e) => setTerrain(e.target.value)}
            className="h-[34px] max-w-[168px] rounded border border-[#2e3a35] bg-[#222c28] px-2.5 py-1.5 text-[13px] text-[#e0e6e3]"
          >
            {TERRAIN_TYPES.map((t) => (
              <option key={t} value={t}>
                {terrainLabel(t)}
              </option>
            ))}
          </select>
          <GhostButton type="button" onClick={() => resetTerrainCombo()}>
            Reset
          </GhostButton>
        </BrushRow>

        <ToolbarSectionTitle>Structure brush</ToolbarSectionTitle>
        <BrushRow>
          <select
            value={selectedStructure ?? ""}
            onChange={(e) =>
              setStructure(e.target.value === "" ? null : e.target.value)
            }
            className="h-[34px] max-w-[168px] rounded border border-[#2e3a35] bg-[#222c28] px-2.5 py-1.5 text-[13px] text-[#e0e6e3]"
            title="Pick None to erase. Neutral = unowned (not Capital)."
          >
            <option value="">none</option>
            {STRUCTURE_TYPES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          <GhostButton
            type="button"
            onClick={() => {
              setStructure(null);
              useEditorStore.getState().setStatus("Structure brush cleared");
            }}
          >
            Clear
          </GhostButton>
        </BrushRow>

        <ToolbarSectionTitle>Unit brush</ToolbarSectionTitle>
        <BrushRow>
          <select
            value={selectedUnit ?? ""}
            onChange={(e) =>
              setUnit(e.target.value === "" ? null : e.target.value)
            }
            className="h-[34px] max-w-[168px] rounded border border-[#2e3a35] bg-[#222c28] px-2.5 py-1.5 text-[13px] text-[#e0e6e3]"
            title="Pick None to remove units from painted tiles."
          >
            <option value="">none</option>
            {UNIT_SPRITE_IDS.map((id) => (
              <option key={id} value={id}>
                {id.replaceAll("-", " ")}
              </option>
            ))}
          </select>
          <GhostButton
            type="button"
            onClick={() => {
              setUnit(null);
              useEditorStore.getState().setStatus("Unit brush cleared");
            }}
          >
            Clear
          </GhostButton>
        </BrushRow>

        <ToolbarSectionTitle>Resources</ToolbarSectionTitle>
        <label className="mt-2 flex cursor-pointer items-center gap-2 text-sm text-[#e0e6e3]">
          <input
            type="checkbox"
            checked={oreMode}
            onChange={(e) => setOre(e.target.checked)}
            className="rounded border-[#3d4d45]"
          />
          Ore deposit brush (LMB toggles on tile)
        </label>

        <ToolbarSectionTitle>Brush faction</ToolbarSectionTitle>
        <select
          value={brushTeamId}
          onChange={(e) => setBrush(Number(e.target.value))}
          className="mt-2 h-[34px] max-w-[168px] rounded border border-[#2e3a35] bg-[#222c28] px-2.5 py-1.5 text-[13px] text-[#e0e6e3]"
        >
          <option value={0}>Neutral (structures only)</option>
          <option value={1}>Faction 1 (Indigo)</option>
          <option value={2}>Faction 2 (Blue)</option>
          <option value={3}>Faction 3 (Green)</option>
          <option value={4}>Faction 4 (Yellow)</option>
        </select>

        <ToolbarSectionTitle>Factions on map</ToolbarSectionTitle>
        <input
          type="number"
          min={2}
          max={4}
          className="mt-2 w-full rounded border border-[#2e3a35] bg-[#222c28] px-2.5 py-1.5 text-[13px] text-[#e0e6e3]"
          value={map.teamCount}
          onChange={(e) => setTeamCount(Number(e.target.value))}
        />

        <div className="my-4 h-px w-full bg-[#2e3a35]" />

        <ToolbarSectionTitle>Bulk</ToolbarSectionTitle>
        <details className="mt-2 text-sm text-[#e0e6e3]">
          <summary className="cursor-pointer text-[#a5b1ac]">
            Bulk actions ▾
          </summary>
          <div className="mt-2 flex flex-col gap-2 py-2">
            <GhostButtonFull
              type="button"
              onClick={() => fillTerrain(selectedTerrain)}
            >
              Fill map with current terrain
            </GhostButtonFull>
            <GhostButtonFull type="button" onClick={clearStructs}>
              Clear all structures
            </GhostButtonFull>
            <GhostButtonFull type="button" onClick={clearUnits}>
              Clear all units
            </GhostButtonFull>
            <GhostButtonFull type="button" onClick={resetPlain}>
              Reset entire map to plains (destructive)
            </GhostButtonFull>
          </div>
        </details>

        <div className="my-4 h-px w-full bg-[#2e3a35]" />

        <ToolbarSectionTitle>Map name</ToolbarSectionTitle>
        <input
          className="mt-2 w-full rounded border border-[#2e3a35] bg-[#222c28] px-2.5 py-1.5 text-[13px] text-[#e0e6e3]"
          value={mapName}
          placeholder="stored as local download name"
          onChange={(e) => setMapNameField(e.target.value)}
        />

        <PrimaryButton className="mt-3" type="button" onClick={saveDownload}>
          Save Map
        </PrimaryButton>

        <GhostButtonFull type="button" className="mt-2" onClick={() => {
          setUploadSlug(slugFromStem(mapName));
          setUploadOpen(true);
        }}>
          Upload to server…
        </GhostButtonFull>

        <input
          ref={fileRef}
          type="file"
          accept="application/json,.json"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void loadJsonFile(file);
            e.target.value = "";
          }}
        />

        <GhostButtonFull type="button" className="mt-2" onClick={() => fileRef.current?.click()}>
          Load Map
        </GhostButtonFull>

        <Link
          href="/"
          className="mt-4 block w-full rounded border border-transparent px-2 py-1.5 text-center text-[13px] text-[#e0e6e3] hover:border-[#2e3a35] hover:bg-[#2a3530]"
        >
          Exit Builder
        </Link>
      </aside>

      <section className="flex min-h-0 min-w-0 flex-1 flex-col bg-[#0f1412]">
        <div className="flex flex-shrink-0 flex-wrap items-center justify-end gap-2 border-b border-[#2e3a35] bg-[#1a221f] px-3 py-2 text-[#e0e6e3]">
          <GridCornerControls key={`${map.width}-${map.height}-${mapName}`} />
        </div>

        <EditorBoardCanvas className="flex-1" />

        <footer className="flex-shrink-0 border-t border-[#2e3a35] bg-[#1a221f] px-4 py-2 text-[13px] text-[#e0e6e3]">
          <span className="font-bold uppercase text-[#4caf50]">Status </span>
          {statusLine}
        </footer>
      </section>

      {uploadOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4">
          <div className="w-full max-w-md rounded-lg border border-[#2e3a35] bg-[#1a221f] p-5 shadow-xl">
            <h2 className="text-lg font-semibold text-[#e0e6e3]">
              Upload map to server
            </h2>
            <p className="mt-2 text-xs text-[#a5b1ac]">
              Only this step reaches the Battalion server (&nbsp;
              <code className="text-[#81c784]">POST /api/maps</code>
              ).
            </p>
            <div className="mt-4 flex flex-col gap-3">
              <label className="text-xs text-[#a5b1ac]">
                Server URL
                <input
                  className="mt-1 w-full rounded border border-[#2e3a35] bg-[#222c28] px-2.5 py-1.5 text-[13px] text-[#e0e6e3]"
                  value={uploadOrigin}
                  onChange={(e) => setUploadOrigin(e.target.value)}
                />
              </label>
              <label className="text-xs text-[#a5b1ac]">
                Slug
                <input
                  className="mt-1 w-full rounded border border-[#2e3a35] bg-[#222c28] px-2.5 py-1.5 text-[13px] text-[#e0e6e3]"
                  value={uploadSlug}
                  onChange={(e) => setUploadSlug(e.target.value.toLowerCase())}
                />
              </label>
              <label className="text-xs text-[#a5b1ac]">
                Owner label
                <input
                  className="mt-1 w-full rounded border border-[#2e3a35] bg-[#222c28] px-2.5 py-1.5 text-[13px] text-[#e0e6e3]"
                  value={uploadOwner}
                  onChange={(e) => setUploadOwner(e.target.value)}
                />
              </label>
            </div>
            <div className="mt-6 flex gap-3">
              <button
                type="button"
                className="flex-1 rounded bg-[#2a3530] px-3 py-2 text-sm font-medium text-[#e0e6e3] hover:bg-[#3d4d45]"
                onClick={() => setUploadOpen(false)}
                disabled={uploadBusy}
              >
                Cancel
              </button>
              <button
                type="button"
                className="flex-1 rounded bg-[#4caf50] px-3 py-2 text-sm font-bold text-black hover:bg-[#8bc34a]"
                disabled={uploadBusy}
                onClick={() => void runUpload()}
              >
                {uploadBusy ? "Uploading…" : "Upload"}
              </button>
            </div>
          </div>
        </div>
      ) : null}

    </div>
  );
}

function ToolbarSectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <p className="mb-1 mt-3 text-[10px] font-bold uppercase tracking-wide text-[#a5b1ac]">
      {children}
    </p>
  );
}

function BrushRow({ children }: { children: React.ReactNode }) {
  return (
    <div className="mt-1 flex max-w-[264px] items-center gap-2">{children}</div>
  );
}

function GhostButton({
  children,
  ...rest
}: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      {...rest}
      className={`shrink-0 rounded border border px-2 py-1 text-xs font-medium text-[#e0e6e3] hover:border-[#2e3a35] hover:bg-[#2a3530] ${rest.className ?? ""}`}
    >
      {children}
    </button>
  );
}

function GhostPadButton(props: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      {...props}
      className="rounded px-3 py-1 text-sm font-semibold leading-none text-[#e0e6e3] hover:bg-[#2a3530]"
    >
      {props.children}
    </button>
  );
}

function GhostButtonFull({
  children,
  ...rest
}: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      {...rest}
      className={`w-full rounded border border-transparent px-2 py-1.5 text-left text-[13px] text-[#e0e6e3] hover:border-[#2e3a35] hover:bg-[#2a3530] ${rest.className ?? ""}`}
    >
      {children}
    </button>
  );
}

function PrimaryButton(props: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const { className, children, ...rest } = props;
  return (
    <button
      {...rest}
      className={`w-full rounded bg-[#4caf50] py-2 text-sm font-bold text-black hover:bg-[#8bc34a] ${className ?? ""}`}
    >
      {children}
    </button>
  );
}
