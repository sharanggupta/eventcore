import { RegisterWebhookForm } from "@/components/register-webhook";
import { GlassCard, TypeBadge, ago } from "@/components/ui";
import { listWebhooks } from "@/lib/eventcore";
import { removeAction } from "./actions";

export default async function WebhooksPage() {
  const webhooks = await listWebhooks();

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Webhooks</h1>

      <GlassCard title="Register a webhook">
        <RegisterWebhookForm />
      </GlassCard>

      <GlassCard className="!p-0">
        <div className="grid grid-cols-[2fr_1fr_1fr_6rem_5rem] gap-3 border-b border-white/10 px-4 py-2 text-xs font-semibold uppercase tracking-widest text-slate-500">
          <span>Endpoint</span><span>Event types</span><span>Payload fields</span><span>Created</span><span></span>
        </div>
        {webhooks.map((webhook) => (
          <div key={webhook.id}
               className="grid grid-cols-[2fr_1fr_1fr_6rem_5rem] items-center gap-3 border-b border-white/5 px-4 py-3 last:border-0"
               data-testid="webhook-row">
            <span className="mono truncate text-sm text-slate-200">{webhook.url}</span>
            <span className="flex flex-wrap gap-1">
              {webhook.eventTypes?.map((type) => <TypeBadge key={type} type={type} />) ??
                <span className="text-xs text-slate-500">all</span>}
            </span>
            <span className="flex flex-wrap gap-1">
              {webhook.payloadFields?.map((field) => <TypeBadge key={field} type={field} />) ??
                <span className="text-xs text-slate-500">full payload</span>}
            </span>
            <span className="text-xs text-slate-500">{ago(webhook.createdAt)}</span>
            <form action={removeAction.bind(null, webhook.id)}>
              <button className="rounded-xl px-3 py-1.5 text-xs font-medium text-rose-300 transition hover:bg-rose-400/10">
                Delete
              </button>
            </form>
          </div>
        ))}
        {webhooks.length === 0 && (
          <div className="px-4 py-8 text-center text-sm text-slate-500">
            no webhooks yet — register one above
          </div>
        )}
      </GlassCard>

      <p className="text-xs text-slate-600">
        Deleting a subscription removes its delivery history with it. Deliveries
        are signed with each webhook&apos;s secret — receivers should verify
        X-EventCore-Signature before trusting a payload.
      </p>
    </div>
  );
}
