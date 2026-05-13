import type { ReactNode } from "react";
import { MainNav } from "@/components/shell/MainNav";

export function AppChrome({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh min-h-0 flex-col text-zinc-100">
      <MainNav />
      <div className="relative z-0 mx-auto flex min-h-0 lg:w-5/6 flex-1 flex-col px-4 py-6">
        {children}
      </div>
    </div>
  );
}
