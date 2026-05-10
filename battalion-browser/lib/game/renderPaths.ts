/** Converts Java {@code TerrainType#name()} e.g. PLAINS_1 to PNG stem Plains_1. */
export function terrainEnumNameToAssetStem(enumName: string): string {
  return enumName
    .split("_")
    .map((part) => (/^\d+$/.test(part) ? part : part.charAt(0) + part.slice(1).toLowerCase()))
    .join("_");
}

export function terrainTextureUrl(terrainEnumName: string): string {
  const stem = terrainEnumNameToAssetStem(terrainEnumName);
  return `/assets/terrain/${stem}.png`;
}

export function structureTextureUrl(structureTypeName: string | null): string | null {
  if (!structureTypeName) return null;
  return `/assets/structures/${structureTypeName}.png`;
}

export function unitTextureUrl(unitTypeName: string): string {
  return `/assets/units/movement/${unitTypeName}.png`;
}

/** Team-mask PNGs: R=1 pixels are replaced with team colour at render time. */
export function uncolouredUnitTextureUrl(unitTypeName: string): string {
  return `/assets/units/movement/uncoloured/${unitTypeName}.png`;
}

/** Sheet used for in-map movement animation (team-masked). */
export function uncolouredMovementSheetUrl(unitTypeName: string): string {
  return `/assets/units/movement/uncoloured/${unitTypeName.toLowerCase()}.png`;
}

/** Sheet used for attack animation when present. */
export function uncolouredAttackSheetUrl(unitTypeName: string): string {
  return `/assets/units/attack/uncoloured/${unitTypeName.toLowerCase()}.png`;
}

export function uncolouredStructureTextureUrl(structureTypeName: string): string {
  return `/assets/structures/uncoloured/${structureTypeName}.png`;
}
