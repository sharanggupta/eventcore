import { Bars, Donut } from "@/components/charts";
import { GlassCard, StatCard, TypeBadge, ago } from "@/components/ui";
import { isConfigured, listEvents, metrics } from "@/lib/eventcore";

export default async function OverviewPage() {
  if (!isConfigured()) return <SetupHelp />;

  const [pipeline, recent] = await Promise.all([
    metrics(),
    listEvents({ limit: 200 }),
  ]);
  const { deliveries, attempts } = pipeline;
  const totalDeliveries = deliveries.pending + deliveries.delivered + deliveries.failed;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Overview</h1>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Events in the log" value={pipeline.eventsIngestedTotal.toLocaleString()} />
        <StatCard label="Delivered" value={deliveries.delivered.toLocaleString()} tone="good" />
        <StatCard label="Dead-lettered" value={deliveries.failed.toLocaleString()}
          tone={deliveries.failed > 0 ? "bad" : "default"}
          hint={deliveries.failed > 0 ? "recover from the Deliveries tab" : "all clear"} />
        <StatCard label="Oldest pending" value={`${pipeline.oldestPendingAgeSeconds}s`}
          hint="age of the delivery backlog" />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <GlassCard title="Delivery pipeline">
          <Donut
            centerLabel={`${totalDeliveries} deliveries`}
            segments={[
              { label: "delivered", value: deliveries.delivered, color: "#34d399" },
              { label: "pending", value: deliveries.pending, color: "#fbbf24" },
              { label: "failed", value: deliveries.failed, color: "#fb7185" },
            ]}
          />
        </GlassCard>

        <GlassCard title="Ingest - last 24 hours (latest 200 events)">
          <Bars data={eventsPerHour(recent.items.map((event) => event.time))} unit="events / hour" />
        </GlassCard>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <GlassCard title="Delivery attempts">
          <Donut
            centerLabel={`${attempts.accepted + attempts.rejected} attempts`}
            segments={[
              { label: "accepted (2xx)", value: attempts.accepted, color: "#34d399" },
              { label: "rejected", value: attempts.rejected, color: "#fb7185" },
            ]}
          />
        </GlassCard>

        <GlassCard title="Flow health - when each type last arrived">
          <table className="w-full text-sm">
            <tbody>
              {pipeline.lastReceived.slice(0, 8).map((row) => (
                <tr key={row.type} className="border-b border-white/5 last:border-0">
                  <td className="py-2"><TypeBadge type={row.type} /></td>
                  <td className="py-2 text-right text-slate-400">
                    {ago(new Date(row.epochSeconds * 1000).toISOString())}
                  </td>
                </tr>
              ))}
              {pipeline.lastReceived.length === 0 && (
                <tr><td className="py-2 text-slate-500">no events yet</td></tr>
              )}
            </tbody>
          </table>
        </GlassCard>
      </div>
    </div>
  );
}

function eventsPerHour(times: string[]): { label: string; value: number }[] {
  const now = Date.now();
  const buckets = Array.from({ length: 24 }, (_, i) => {
    const hour = new Date(now - (23 - i) * 3600_000);
    return { label: `${hour.getUTCHours()}:00`, value: 0, start: now - (24 - i) * 3600_000 };
  });
  for (const time of times) {
    const t = new Date(time).getTime();
    const bucket = buckets.find((b) => t >= b.start && t < b.start + 3600_000);
    if (bucket) bucket.value += 1;
  }
  return buckets.map(({ label, value }) => ({ label, value }));
}

function SetupHelp() {
  return (
    <GlassCard title="Connect your instance" className="max-w-xl">
      <ol className="list-decimal space-y-2 pl-5 text-sm text-slate-300">
        <li>Copy <code className="mono">.env.local.example</code> to <code className="mono">.env.local</code></li>
        <li>Set <code className="mono">EVENTCORE_URL</code> and <code className="mono">EVENTCORE_API_KEY</code></li>
        <li>Restart <code className="mono">npm run dev</code></li>
      </ol>
    </GlassCard>
  );
}
