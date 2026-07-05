import Link from "next/link";
import { EventRow } from "@/components/event-row";
import { GlassCard } from "@/components/ui";
import { listEvents, resolveTimeRange } from "@/lib/eventcore";
import { TimeRange } from "@/components/time-range";

export default async function EventsPage({ searchParams }: {
  searchParams: Promise<{ type?: string; cursor?: string; since?: string; from?: string; to?: string; pf?: string; pv?: string }>;
}) {
  const { type, cursor, since, from, to, pf, pv } = await searchParams;
  const range = resolveTimeRange({ since, from, to });
  const page = await listEvents({
    type, cursor, limit: 25, ...range,
    payloadField: pf, payloadValue: pv,
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">Events</h1>
        <form className="flex flex-wrap gap-2" action="/events">
          <input
            name="type"
            defaultValue={type ?? ""}
            placeholder="type, e.g. order.placed"
            className="glass w-52 px-4 py-2 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:ring-1 focus:ring-cyan-400/50"
          />
          <input
            name="pf"
            defaultValue={pf ?? ""}
            placeholder="payload field, e.g. userId"
            className="glass w-48 px-4 py-2 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:ring-1 focus:ring-cyan-400/50"
            data-testid="payload-field"
          />
          <input
            name="pv"
            defaultValue={pv ?? ""}
            placeholder="value, e.g. u_123"
            className="glass w-40 px-4 py-2 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:ring-1 focus:ring-cyan-400/50"
            data-testid="payload-value"
          />
          <button className="glass px-4 py-2 text-sm font-medium text-cyan-300 transition hover:bg-white/10">
            Filter
          </button>
        </form>
      </div>

      <TimeRange basePath="/events" keep={{ type, pf, pv }} since={since} from={from} to={to} />

      <GlassCard className="!p-0">
        {page.items.map((event) => <EventRow key={event.id} event={event} />)}
        {page.items.length === 0 && (
          <div className="px-4 py-8 text-center text-sm text-slate-500">
            no events match{type ? ` type ${type}` : ""}{pf ? ` payload.${pf}=${pv}` : ""} - adjust the filters or the time range
          </div>
        )}
      </GlassCard>

      <div className="flex items-center gap-3">
        {(cursor || undefined) && (
          <Link
            href={`/events?${new URLSearchParams(Object.entries({ type, pf, pv, since, from, to }).filter(([, v]) => v) as [string, string][])}`}
            className="glass px-4 py-2 text-sm text-slate-300 transition hover:bg-white/10">
            ⟵ Newest
          </Link>
        )}
        {page.nextCursor && (
          <Link
            href={`/events?${new URLSearchParams(Object.entries({ type, pf, pv, since, from, to, cursor: page.nextCursor }).filter(([, v]) => v) as [string, string][])}`}
            className="glass px-4 py-2 text-sm text-cyan-300 transition hover:bg-white/10"
            data-testid="older-page"
          >
            Older ⟶
          </Link>
        )}
      </div>
    </div>
  );
}
