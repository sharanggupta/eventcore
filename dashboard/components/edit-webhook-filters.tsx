"use client";

import { useActionState, useState } from "react";
import { updateFiltersAction, type UpdateResult } from "@/app/webhooks/actions";

/** Inline PATCH editor: change a subscription's filters, keep its id and secret. */
export function EditWebhookFilters({ id, eventTypes, payloadFields }: {
  id: string;
  eventTypes?: string[];
  payloadFields?: string[];
}) {
  const [open, setOpen] = useState(false);
  const [result, submit, pending] = useActionState<UpdateResult, FormData>(
    updateFiltersAction.bind(null, id), null);

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} data-testid="edit-filters"
        className="rounded-xl px-3 py-1.5 text-xs font-medium text-slate-400 transition hover:bg-white/5 hover:text-slate-200">
        Edit filters
      </button>
    );
  }

  return (
    <form action={submit} className="col-span-full grid gap-2 py-2 lg:grid-cols-[1fr_1fr_auto_auto]"
          data-testid="edit-filters-form">
      <input name="eventTypes" defaultValue={eventTypes?.join(", ") ?? ""}
        placeholder="event types (blank = all)"
        className="glass px-3 py-1.5 text-xs text-slate-200 placeholder:text-slate-600 focus:outline-none" />
      <input name="payloadFields" defaultValue={payloadFields?.join(", ") ?? ""}
        placeholder="payload fields (blank = full)"
        className="glass px-3 py-1.5 text-xs text-slate-200 placeholder:text-slate-600 focus:outline-none" />
      <button disabled={pending}
        className="glass px-3 py-1.5 text-xs font-medium text-cyan-300 transition hover:bg-white/10 disabled:opacity-50">
        {pending ? "Saving…" : "Save"}
      </button>
      <button type="button" onClick={() => setOpen(false)}
        className="px-3 py-1.5 text-xs text-slate-500 hover:text-slate-300">
        Cancel
      </button>
      {result && !result.ok && (
        <div className="col-span-full text-xs text-rose-300">{result.error}</div>
      )}
    </form>
  );
}
