import Link from "next/link";

export default function HomePage() {
  return (
    <div className="flex flex-1 flex-col p-4 backdrop-blur-sm bg-zinc-900/50 rounded-xl">
      <div>
        <h1 className="text-5xl font-semibold text-center text-zinc-100 bg-red-800 p-2 rounded-md">
          Battalion Revival
        </h1>
        <p className="mt-2 lg:w-3/4 text-zinc-200">
          A Project to revive and pay homage to the original Battalion Series. A tactical turn-based strategy game where up to 4 teams go head to head with land, sea and air in a battle of strength, tactics and teamplay to emerge victorious!
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Link
          href="/play"
          className="rounded-xl border border-zinc-800 bg-zinc-900 p-5 transition-colors hover:border-emerald-800/60 hover:bg-zinc-900/50"
        >
          <h2 className="font-medium text-emerald-400">Play</h2>
          <p className="mt-2 text-sm text-zinc-500">
            Connect WebSocket to a match and view the authoritative map.
          </p>
        </Link>
        <Placeholder href="/auth" title="Sign in" desc="Account and session (to be wired to the server)." />
        <Placeholder
          href="/matchmaking"
          title="Matchmaking"
          desc="Open or join a lobby, pick a map, start — then everyone lands in Play connected to the match."
        />
        <Placeholder href="/replays" title="Replay browser" desc="Load recorded games from the server or disk." />
        <Placeholder href="/levels" title="Community levels" desc="Browse and download shared maps." />
        <Placeholder href="/editor" title="Level editor" desc="Author maps (Pixi viewport + editor tools)." />
        <Placeholder href="/settings" title="Settings" desc="Audio, graphics, keybindings, account." />
      </div>
    </div>
  );
}

function Placeholder({
  href,
  title,
  desc,
}: {
  href: string;
  title: string;
  desc: string;
}) {
  return (
    <Link
      href={href}
      className="rounded-xl border border-zinc-800 bg-zinc-900 p-5 transition-colors hover:border-zinc-700 hover:bg-zinc-900/50"
    >
      <h2 className="font-medium text-zinc-200">{title}</h2>
      <p className="mt-2 text-sm text-zinc-500">{desc}</p>
    </Link>
  );
}
