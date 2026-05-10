/**
 * REST map catalog — mirrors {@link com.game.network.client.MapCatalogClient#uploadMap}.
 */

export interface MapUploadRequest {
  slug: string;
  ownerUsername: string;
  mapJson: string;
  schemaVersion: number;
}

export async function uploadMapToServer(
  serverRoot: string,
  slug: string,
  ownerUsername: string,
  mapJson: string
): Promise<string> {
  const root = normalizeServerRoot(serverRoot);
  const body: MapUploadRequest = {
    slug,
    ownerUsername: ownerUsername?.trim() || "anonymous",
    mapJson,
    schemaVersion: 1,
  };

  const res = await fetch(`${root}/api/maps`, {
    method: "POST",
    headers: { "Content-Type": "application/json; charset=utf-8" },
    body: JSON.stringify(body),
  });

  const text = await res.text();
  if (!res.ok) {
    throw new Error(text || `${res.status} ${res.statusText}`);
  }
  return text || res.statusText;
}

export function normalizeServerRoot(serverRoot: string): string {
  if (!serverRoot?.trim()) {
    throw new Error("Server URL is required.");
  }
  let s = serverRoot.trim();
  while (s.endsWith("/")) {
    s = s.slice(0, -1);
  }
  return s;
}
