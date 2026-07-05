import Link from "next/link";
import { SINCE_PRESETS } from "@/lib/eventcore";

/** Kibana-style time filter: preset chips plus a custom from/to (UTC) form. */
export function TimeRange({ basePath, keep, since, from, to }: {
  basePath: string;
  keep: Record<string, string | undefined>;
  since?: string;
  from?: string;
  to?: string;
}) {
  const kept = Object.entries(keep).filter(([, value]) => value) as [string, string][];
  const hrefWith = (extra: Record<string, string>) => {
    const search = new URLSearchParams([...kept, ...Object.entries(extra)]);
    const qs = search.toString();
    return qs ? `${basePath}?${qs}` : basePath;
  };
  const customActive = Boolean(from || to);

  return (
    <div className="flex flex-wrap items-center gap-2" data-testid="time-range">
      {["all", ...SINCE_PRESETS].map((preset) => {
        const active = preset === "all" ? !since && !customActive : since === preset;
        return (
          <Link
            key={preset}
            href={preset === "all" ? hrefWith({}) : hrefWith({ since: preset })}
            className={`rounded-full px-3 py-1 text-xs font-medium transition ${
              active ? "glass text-cyan-300" : "text-slate-500 hover:text-slate-300"
            }`}
            data-testid={`since-${preset}`}
          >
            {preset === "all" ? "All time" : `Last ${preset}`}
          </Link>
        );
      })}
      <form action={basePath} className="ml-2 flex items-center gap-1.5">
        {kept.map(([name, value]) => (
          <input key={name} type="hidden" name={name} value={value} />
        ))}
        <input type="datetime-local" name="from" defaultValue={from?.slice(0, 16)}
          className="glass px-2 py-1 text-xs text-slate-300 focus:outline-none" title="from (UTC)" />
        <span className="text-xs text-slate-600">→</span>
        <input type="datetime-local" name="to" defaultValue={to?.slice(0, 16)}
          className="glass px-2 py-1 text-xs text-slate-300 focus:outline-none" title="to (UTC)" />
        <button className={`rounded-full px-3 py-1 text-xs font-medium transition ${
          customActive ? "glass text-cyan-300" : "text-slate-500 hover:text-slate-300"
        }`}>
          Apply
        </button>
      </form>
    </div>
  );
}
