"use client";

import { useState } from "react";
import type { Event } from "@/lib/eventcore";
import { TypeBadge, fmtTime } from "@/components/ui";

/** One event per row, newest first; click anywhere on the row for full details. */
export function EventRow({ event }: { event: Event }) {
  const [open, setOpen] = useState(false);

  return (
    <div className="border-b border-white/5 last:border-0" data-testid="event-row">
      <button
        onClick={() => setOpen(!open)}
        className="grid w-full grid-cols-[11rem_1fr_auto] items-center gap-3 px-4 py-3 text-left transition hover:bg-white/5"
        aria-expanded={open}
      >
        <span className="mono text-xs text-slate-400">{fmtTime(event.time)}</span>
        <span><TypeBadge type={event.type} /></span>
        <span className="mono text-xs text-slate-600">{event.id.slice(0, 8)}…</span>
      </button>
      {open && (
        <div className="space-y-2 px-4 pb-4" data-testid="event-detail">
          <div className="mono text-xs text-slate-500">id {event.id}</div>
          <pre className="glass mono overflow-x-auto p-4 text-xs leading-relaxed text-cyan-100">
            {JSON.stringify(event.payload, null, 2) ?? "null"}
          </pre>
        </div>
      )}
    </div>
  );
}
