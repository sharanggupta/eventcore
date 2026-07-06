import { GlassCard, TypeBadge, ago } from "@/components/ui";
import { pullFleet } from "@/lib/eventcore";

/** The screen no competitor has: every pull consumer's position and lag. */
export default async function ConsumersPage() {
  const fleet = await pullFleet();

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Pull consumers</h1>

      <GlassCard className="!p-0">
        <div className="grid grid-cols-[1fr_7rem_8rem_1fr] gap-3 border-b border-white/10 px-4 py-2 text-xs font-semibold uppercase tracking-widest text-slate-500">
          <span>Consumer</span><span>Lag</span><span>Position</span><span>Types</span>
        </div>
        {fleet.map((consumer) => (
          <div key={consumer.name}
               className="grid grid-cols-[1fr_7rem_8rem_1fr] items-center gap-3 border-b border-white/5 px-4 py-3 last:border-0"
               data-testid="consumer-row">
            <span className="mono text-sm text-slate-200">{consumer.name}</span>
            <span className={`mono text-sm font-semibold ${
              consumer.lagEvents > 0 ? "text-amber-300" : "text-emerald-300"
            }`}>
              {consumer.lagEvents.toLocaleString()}
            </span>
            <span className="text-xs text-slate-500">
              {consumer.position === "beginning" ? "beginning" : ago(consumer.position)}
            </span>
            <span className="flex flex-wrap gap-1">
              {consumer.eventTypes.includes("*")
                ? <span className="text-xs text-slate-500">all types</span>
                : consumer.eventTypes.map((type) => <TypeBadge key={type} type={type} />)}
            </span>
          </div>
        ))}
        {fleet.length === 0 && (
          <div className="px-4 py-8 text-center text-sm text-slate-500">
            no pull consumers yet - create one with POST /v1/pull-subscriptions
          </div>
        )}
      </GlassCard>

      <p className="text-xs text-slate-600">
        Lag is the number of events a consumer has not committed past yet.
        A growing lag names the stuck consumer before it matters.
      </p>
    </div>
  );
}
