import Link from "next/link";

const links: { href: string; label: string }[] = [
  { href: "/play", label: "Play" },
  { href: "/auth", label: "Sign in" },
  { href: "/matchmaking", label: "Matchmaking" },
  { href: "/levels", label: "Community levels" },
  { href: "/editor", label: "Level editor" },
  { href: "/settings", label: "Settings" },
];

export function MainNav() {
  return (
    <header className="border-b border-zinc-800 bg-zinc-950/80 backdrop-blur">
        <div className="mx-auto flex max-w-screen-2xl flex-wrap items-center justify-between gap-3 px-4 py-3">
        <Link href="/" className="text-lg font-semibold tracking-tight text-zinc-100">
          Battalion Revival
        </Link>
        <nav className="flex flex-wrap items-center gap-1 sm:gap-2">
          {links.map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              className="rounded-md px-2.5 py-1.5 text-sm text-zinc-400 transition-colors hover:bg-zinc-800 hover:text-zinc-100"
            >
              {label}
            </Link>
          ))}
        </nav>
      </div>
    </header>
  );
}
