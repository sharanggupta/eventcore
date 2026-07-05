import Link from "next/link";
import { GlassCard, StatusBadge, ago } from "@/components/ui";
import { listDeliveries } from "@/lib/eventcore";

const TABS = ["all", "pending", "delivered", "failed"] as const;

export default async function DeliveriesPage({ searchParams }: {
  searchParams: Promise<{ status?: string; cursor?: string }>;
}) {
  const { status, cursor } = await searchParams;
  const active = status ?? "all";
  const page = await listDeliveries({
    status: active === "all" ? undefined : active,
    cursor,
    limit: 25,
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">Deliveries</h1>
        <div className="flex gap-1">
          {TABS.map((tab) => (
            <Link
              key={tab}
              href={tab === "all" ? "/deliveries" : `/deliveries?status=${tab}`}
              className={`rounded-xl px-4 py-2 text-sm font-medium transition ${
                active === tab
                  ? "glass text-white"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              {tab}
            </Link>
          ))}
        </div>
      </div>

      <GlassCard className="!p-0">
        <div className="grid grid-cols-[6rem_1fr_5rem_6rem] gap-3 border-b border-white/10 px-4 py-2 text-xs font-semibold uppercase tracking-widest text-slate-500">
          <span>Status</span><span>Delivery</span><span>Attempts</span><span>Created</span>
        </div>
        {page.items.map((delivery) => (
          <Link
            key={delivery.id}
            href={`/deliveries/${delivery.id}`}
            className="grid grid-cols-[6rem_1fr_5rem_6rem] items-center gap-3 border-b border-white/5 px-4 py-3 transition last:border-0 hover:bg-white/5"
            data-testid="delivery-row"
          >
            <span><StatusBadge status={delivery.status} /></span>
            <span className="mono truncate text-xs text-slate-400">{delivery.id}</span>
            <span className="mono text-sm text-slate-300">{delivery.attempts}</span>
            <span className="text-xs text-slate-500">{ago(delivery.createdAt)}</span>
          </Link>
        ))}
        {page.items.length === 0 && (
          <div className="px-4 py-8 text-center text-sm text-slate-500">
            no {active === "all" ? "" : active} deliveries
          </div>
        )}
      </GlassCard>

      {page.nextCursor && (
        <Link
          href={`/deliveries?${active !== "all" ? `status=${active}&` : ""}cursor=${encodeURIComponent(page.nextCursor)}`}
          className="glass inline-block px-4 py-2 text-sm text-cyan-300 transition hover:bg-white/10"
        >
          Older ⟶
        </Link>
      )}
    </div>
  );
}
