import Link from "next/link";
import { revalidatePath } from "next/cache";
import { GlassCard, StatusBadge, fmtTime } from "@/components/ui";
import { deliveryDetail, redeliver } from "@/lib/eventcore";

export default async function DeliveryDetailPage({ params }: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const delivery = await deliveryDetail(id);

  async function redeliverNow() {
    "use server";
    await redeliver(id);
    revalidatePath(`/deliveries/${id}`);
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link href="/deliveries" className="text-sm text-slate-500 hover:text-slate-300">
            ⟵ Deliveries
          </Link>
          <h1 className="mono mt-1 text-xl font-bold text-white">{delivery.id}</h1>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge status={delivery.status} />
          {delivery.status === "failed" && (
            <form action={redeliverNow}>
              <button className="glass px-4 py-2 text-sm font-medium text-cyan-300 transition hover:bg-white/10">
                Redeliver now
              </button>
            </form>
          )}
        </div>
      </div>

      <GlassCard title={`Attempt history - ${delivery.attempts} attempt${delivery.attempts === 1 ? "" : "s"}`}>
        <ol className="space-y-3">
          {delivery.deliveryAttempts.map((attempt) => (
            <li key={attempt.attempt} className="glass flex flex-wrap items-center gap-4 p-3 text-sm"
                data-testid="attempt">
              <span className="mono w-8 text-center text-lg font-bold text-slate-400">
                {attempt.attempt}
              </span>
              <span className={`mono font-semibold ${
                attempt.statusCode && attempt.statusCode < 300 ? "text-emerald-300" : "text-rose-300"
              }`}>
                {attempt.statusCode ?? "no response"}
              </span>
              <span className="mono text-xs text-slate-500">{fmtTime(attempt.attemptedAt)}</span>
              <span className="mono text-xs text-slate-500">{attempt.durationMs}ms</span>
              {attempt.error && (
                <span className="w-full truncate text-xs text-rose-200/80">{attempt.error}</span>
              )}
              {attempt.responseSnippet && (
                <code className="mono w-full truncate text-xs text-slate-400">
                  {attempt.responseSnippet}
                </code>
              )}
            </li>
          ))}
          {delivery.deliveryAttempts.length === 0 && (
            <li className="text-sm text-slate-500">no attempts recorded yet</li>
          )}
        </ol>
      </GlassCard>

      <GlassCard title="Delivery">
        <dl className="mono grid grid-cols-[10rem_1fr] gap-y-2 text-xs text-slate-400">
          <dt className="text-slate-500">event id</dt><dd>{delivery.eventId}</dd>
          <dt className="text-slate-500">subscription id</dt><dd>{delivery.subscriptionId}</dd>
          <dt className="text-slate-500">created</dt><dd>{fmtTime(delivery.createdAt)}</dd>
        </dl>
      </GlassCard>
    </div>
  );
}
