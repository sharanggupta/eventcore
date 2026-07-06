import Link from "next/link";
import { GlassCard, StatusBadge, ago } from "@/components/ui";
import { listDeliveries, resolveTimeRange } from "@/lib/eventcore";
import { TimeRange } from "@/components/time-range";
import { AutoRefresh } from "@/components/auto-refresh";
import { redeliverAction, redeliverAllFailedAction } from "./actions";

const TABS = ["all", "pending", "delivered", "failed"] as const;

export default async function DeliveriesPage({ searchParams }: {
  searchParams: Promise<{ status?: string; cursor?: string; since?: string; from?: string; to?: string }>;
}) {
  const { status, cursor, since, from, to } = await searchParams;
  const active = status ?? "all";
  const range = resolveTimeRange({ since, from, to });
  const page = await listDeliveries({
    status: active === "all" ? undefined : active,
    cursor,
    limit: 25,
    ...range,
  });
  const hasPending = page.items.some((delivery) => delivery.status === "pending");

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

      <TimeRange basePath="/deliveries" keep={{ status }} since={since} from={from} to={to} />
      {hasPending && <AutoRefresh seconds={3} />}

      {active === "failed" && page.items.length > 0 && (
        <form action={redeliverAllFailedAction}>
          <button className="glass px-4 py-2 text-sm font-medium text-cyan-300 transition hover:bg-white/10"
                  data-testid="redeliver-all">
            Redeliver all failed ({page.items.length}{page.nextCursor ? "+" : ""})
          </button>
        </form>
      )}

      <GlassCard className="!p-0">
        <div className="grid grid-cols-[6rem_1fr_5rem_6rem_5rem] gap-3 border-b border-white/10 px-4 py-2 text-xs font-semibold uppercase tracking-widest text-slate-500">
          <span>Status</span><span>Delivery</span><span>Attempts</span><span>Created</span><span></span>
        </div>
        {page.items.map((delivery) => (
          <div
            key={delivery.id}
            className="grid grid-cols-[6rem_1fr_5rem_6rem_5rem] items-center gap-3 border-b border-white/5 px-4 py-2.5 transition last:border-0 hover:bg-white/5"
            data-testid="delivery-row"
          >
            <span><StatusBadge status={delivery.status} /></span>
            <Link href={`/deliveries/${delivery.id}`}
                  className="mono truncate text-xs text-slate-400 underline-offset-4 hover:text-cyan-300 hover:underline">
              {delivery.id}
            </Link>
            <span className="mono text-sm text-slate-300">{delivery.attempts}</span>
            <span className="text-xs text-slate-500">{ago(delivery.createdAt)}</span>
            <span>
              {delivery.status === "failed" && (
                <form action={redeliverAction.bind(null, delivery.id)}>
                  <button className="rounded-xl px-3 py-1.5 text-xs font-medium text-cyan-300 transition hover:bg-cyan-400/10"
                          data-testid="retry-one">
                    Retry
                  </button>
                </form>
              )}
            </span>
          </div>
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
