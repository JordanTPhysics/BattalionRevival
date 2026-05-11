"use client";

import { useLayoutEffect } from "react";
import { hydrateSessionFromBrowserStorage } from "@/stores/sessionStore";

let didHydrate = false;

/**
 * Applies `localStorage` session fields after mount so the first client render matches SSR
 * (both use env default), then persisted origin / match / seat replace it before paint.
 */
export function SessionStorageHydration() {
  useLayoutEffect(() => {
    if (didHydrate) return;
    didHydrate = true;
    hydrateSessionFromBrowserStorage();
  }, []);
  return null;
}
