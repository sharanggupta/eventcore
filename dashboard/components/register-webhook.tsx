"use client";

import { useActionState } from "react";
import { registerAction, type RegisterResult } from "@/app/webhooks/actions";

/** Registration reveals the signing secret exactly once - this form is that once. */
export function RegisterWebhookForm() {
  const [result, submit, pending] = useActionState<RegisterResult, FormData>(registerAction, null);

  return (
    <div className="space-y-4">
      <form action={submit} className="grid gap-3 lg:grid-cols-[2fr_1fr_1fr_auto]">
        <input name="url" required placeholder="https://your-service.example.com/hooks"
          className="glass px-4 py-2 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:ring-1 focus:ring-cyan-400/50" />
        <input name="eventTypes" placeholder="event types (comma-sep, blank = all)"
          className="glass px-4 py-2 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:ring-1 focus:ring-cyan-400/50" />
        <input name="payloadFields" placeholder="payload fields (blank = full)"
          className="glass px-4 py-2 text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:ring-1 focus:ring-cyan-400/50" />
        <button disabled={pending}
          className="glass px-4 py-2 text-sm font-medium text-cyan-300 transition hover:bg-white/10 disabled:opacity-50">
          {pending ? "Registering…" : "Register"}
        </button>
      </form>

      {result?.ok && (
        <div className="glass border-emerald-400/30 p-4" data-testid="secret-reveal">
          <div className="text-sm font-semibold text-emerald-300">
            Webhook registered — copy the signing secret now, it will not be shown again:
          </div>
          <code className="mono mt-2 block break-all text-sm text-emerald-100">
            {result.webhook.secret}
          </code>
        </div>
      )}
      {result && !result.ok && (
        <div className="glass border-rose-400/30 p-4 text-sm text-rose-300" data-testid="register-error">
          {result.error}
        </div>
      )}
    </div>
  );
}
