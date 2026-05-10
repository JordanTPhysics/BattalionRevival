import Link from "next/link";

export default function HomePage() {
  return (
    <div className="flex flex-1 flex-col gap-8">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight text-zinc-100">
          Battalion Revival
        </h1>
        <p className="mt-2 max-w-xl text-zinc-400">
          Browser client: authentication, matchmaking, and the game view talk to the Java server;
          the battlefield is drawn on a single WebGL canvas with PixiJS — not DOM tiles.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Link
          href="/play"
          className="rounded-xl border border-zinc-800 bg-zinc-900/50 p-5 transition-colors hover:border-emerald-800/60 hover:bg-zinc-900"
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
      className="rounded-xl border border-zinc-800 bg-zinc-900/30 p-5 transition-colors hover:border-zinc-700 hover:bg-zinc-900/50"
    >
      <h2 className="font-medium text-zinc-200">{title}</h2>
      <p className="mt-2 text-sm text-zinc-500">{desc}</p>
    </Link>
  );
}
