/** One cell in the Swing map editor ({@link com.game.persistence.MapJsonPersistence}). */
export interface EditorTile {
  terrain: string;
  structure: string | null;
  structureTeam: number | null;
  unitSprite: string | null;
  unitTeam: number | null;
  unitFacing: string;
  oreDeposit: boolean;
}

export interface EditorMapSnapshot {
  width: number;
  height: number;
  teamCount: number;
  tiles: EditorTile[][];
}
