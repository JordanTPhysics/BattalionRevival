import type { ReactNode } from "react";
import { MainNav } from "@/components/shell/MainNav";

export function AppChrome({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh min-h-0 flex-col bg-zinc-950 text-zinc-100">
      <MainNav />
      <div className="mx-auto flex min-h-0 w-full max-w-screen-2xl flex-1 flex-col px-4 py-6">
        {children}
      </div>
    </div>
  );
}
