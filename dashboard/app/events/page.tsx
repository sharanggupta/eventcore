import Link from "next/link";
import { EventRow } from "@/components/event-row";
import { GlassCard } from "@/components/ui";
import { listEvents } from "@/lib/eventcore";

export default async function EventsPage({ searchParams }: {
  searchParams: Promise<{ type?: string; cursor?: string }>;
}) {
  const { type, cursor } = await searchParams;
  const page = await listEvents({ type, cursor, limit: 25 });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">Events</h1>
        <form className="flex gap-2" action="/events">
          <input
            name="type"
            defaultValue={type ?? ""}
            placeholder="filter by type, e.g. order.placed"
            className="glass w-72 px-4 py-2 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:ring-1 focus:ring-cyan-400/50"
          />
          <button className="glass px-4 py-2 text-sm font-medium text-cyan-300 transition hover:bg-white/10">
            Filter
          </button>
        </form>
      </div>

      <GlassCard className="!p-0">
        {page.items.map((event) => <EventRow key={event.id} event={event} />)}
        {page.items.length === 0 && (
          <div className="px-4 py-8 text-center text-sm text-slate-500">
            no events{type ? ` of type ${type}` : ""} - ingest one and refresh
          </div>
        )}
      </GlassCard>

      <div className="flex items-center gap-3">
        {(cursor || undefined) && (
          <Link href={`/events${type ? `?type=${type}` : ""}`}
            className="glass px-4 py-2 text-sm text-slate-300 transition hover:bg-white/10">
            ⟵ Newest
          </Link>
        )}
        {page.nextCursor && (
          <Link
            href={`/events?${type ? `type=${type}&` : ""}cursor=${encodeURIComponent(page.nextCursor)}`}
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
