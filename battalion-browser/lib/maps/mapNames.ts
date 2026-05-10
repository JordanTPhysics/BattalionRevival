/** Mirrors {@link com.game.ui.MapBuilderWindow#sanitizeMapFileStem}. */
export function sanitizeMapFileStem(raw: string): string {
  let out = "";
  for (let i = 0; i < raw.length; i++) {
    const c = raw[i];
    if (
      c === "<" ||
      c === ">" ||
      c === ":" ||
      c === '"' ||
      c === "/" ||
      c === "\\" ||
      c === "|" ||
      c === "?" ||
      c === "*"
    ) {
      out += "_";
      continue;
    }
    if (c === "." && (i === 0 || i === raw.length - 1)) {
      continue;
    }
    out += c;
  }
  let trimmed = out.trim();
  while (trimmed.endsWith(".")) {
    trimmed = trimmed.slice(0, -1).trim();
  }
  return trimmed;
}

const SLUG_SAFE = /^[a-z0-9][a-z0-9-]{1,62}$/;

/** Mirrors {@link com.game.ui.ServerMapUploadDialog#slugFromStem}. */
export function slugFromStem(stem: string | null | undefined): string {
  if (stem == null || stem.trim() === "") {
    return "my-map";
  }
  let s = stem.toLowerCase().trim().replace(/[^a-z0-9-]+/g, "-").replace(/-+/g, "-");
  s = s.replace(/^-+/, "").replace(/-+$/, "");
  if (!s.length) return "my-map";
  if (s.length > 63) {
    s = s.slice(0, 63).replace(/-+$/, "");
  }
  if (s.length < 2) {
    return `map-${s}`;
  }
  return s;
}

export function isValidUploadSlug(slug: string): boolean {
  return SLUG_SAFE.test(slug);
}
