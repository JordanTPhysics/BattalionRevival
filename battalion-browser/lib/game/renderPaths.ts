/** Converts Java {@code TerrainType#name()} e.g. PLAINS_1 to PNG stem Plains_1. */
export function terrainEnumNameToAssetStem(enumName: string): string {
  return enumName
    .split("_")
    .map((part) => (/^\d+$/.test(part) ? part : part.charAt(0) + part.slice(1).toLowerCase()))
    .join("_");
}

/** Stems with authored tiles under {@code public/assets/terrain/animated/} (see TerrainType.java). */
const TERRAIN_STEMS_AUTHORED_AS_ANIMATED_PNG = new Set<string>([
  "Archipelago_1",
  "Archipelago_2",
  "Archipelago_3",
  "Archipelago_4",
  "Enriched_Ore_Deposit_1",
  "Enriched_Ore_Deposit_2",
  "Ore_Deposit_1",
  "Ore_Deposit_2",
  "Reef_1",
  "Reef_2",
  "Reef_3",
  "Reef_4",
  "Rock_Formation_1",
  "Rock_Formation_2",
  "Rock_Formation_3",
  "Rock_Formation_4",
  "Sea_Double_LeftDown",
  "Sea_Double_LeftDown_One_RightUp",
  "Sea_Double_LeftRight",
  "Sea_Double_LeftUp",
  "Sea_Double_LeftUp_One_RightDown",
  "Sea_Double_RightDown",
  "Sea_Double_RightDown_One_LeftUp",
  "Sea_Double_RightUp",
  "Sea_Double_RightUp_One_LeftDown",
  "Sea_Double_UpDown",
  "Sea_Main",
  "Sea_One_LeftDown",
  "Sea_One_LeftDown_One_RightDown",
  "Sea_One_LeftDown_One_RightUp",
  "Sea_One_LeftDown_One_RightUp_One_RightDown",
  "Sea_One_LeftUp",
  "Sea_One_LeftUp_One_LeftDown",
  "Sea_One_LeftUp_One_LeftDown_One_RightDown",
  "Sea_One_LeftUp_One_LeftDown_One_RightUp",
  "Sea_One_LeftUp_One_LeftDown_One_RightUp_One_RightDown",
  "Sea_One_LeftUp_One_RightDown",
  "Sea_One_LeftUp_One_RightUp",
  "Sea_One_LeftUp_One_RightUp_One_RightDown",
  "Sea_One_RightDown",
  "Sea_One_RightUp",
  "Sea_One_RightUp_One_RightDown",
  "Sea_Quadruple_LeftRightUpDown",
  "Sea_Single_Down",
  "Sea_Single_Down_One_LeftUp",
  "Sea_Single_Down_One_LeftUp_One_RightUp",
  "Sea_Single_Down_One_RightUp",
  "Sea_Single_Left",
  "Sea_Single_Left_One_RightDown",
  "Sea_Single_Left_One_RightUp",
  "Sea_Single_Left_One_RightUp_One_RightDown",
  "Sea_Single_Right",
  "Sea_Single_Right_One_LeftDown",
  "Sea_Single_Right_One_LeftUp",
  "Sea_Single_Right_One_LeftUp_One_LeftDown",
  "Sea_Single_Up",
  "Sea_Single_Up_One_LeftDown",
  "Sea_Single_Up_One_LeftDown_One_RightDown",
  "Sea_Single_Up_One_RightDown",
  "Sea_Triple_LeftRightDown",
  "Sea_Triple_LeftRightUp",
  "Sea_Triple_LeftUpDown",
  "Sea_Triple_RightUpDown",
  "Shore_Double_LeftDown",
  "Shore_Double_LeftUp",
  "Shore_Double_RightDown",
  "Shore_Double_RightUp",
  "Shore_Single_Down",
  "Shore_Single_Left",
  "Shore_Single_Right",
  "Shore_Single_Up",
  "Shore_Triple_Down",
  "Shore_Triple_Left",
  "Shore_Triple_Right",
  "Shore_Triple_Up",
  "Volcano",
  "Wasteland",
]);

export function terrainTextureUrl(terrainEnumName: string): string {
  const stem = terrainEnumNameToAssetStem(terrainEnumName);
  if (TERRAIN_STEMS_AUTHORED_AS_ANIMATED_PNG.has(stem)) {
    return `/assets/terrain/animated/${stem}.png`;
  }
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
