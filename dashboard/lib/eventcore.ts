// Server-side EventCore client. The API key lives only in the Node process
// (.env.local) - pages are server components, so nothing sensitive ships to
// the browser and no CORS configuration is needed on the instance.

const BASE = process.env.EVENTCORE_URL ?? "http://localhost:8080";
const KEY = process.env.EVENTCORE_API_KEY ?? "";

export type Event = {
  id: string;
  time: string;
  type: string;
  payload: unknown;
};

export type EventPage = { items: Event[]; nextCursor: string | null };

export type Delivery = {
  id: string;
  eventId: string;
  subscriptionId: string;
  status: "pending" | "delivered" | "failed";
  attempts: number;
  createdAt: string;
};

export type DeliveryPage = { items: Delivery[]; nextCursor: string | null };

export type DeliveryAttempt = {
  attempt: number;
  attemptedAt: string;
  statusCode: number | null;
  error: string | null;
  responseSnippet: string | null;
  durationMs: number;
};

export type DeliveryDetail = Delivery & { deliveryAttempts: DeliveryAttempt[] };

export type PullSubscriptionStatus = {
  name: string;
  position: string | null;
  positionTime: string | null;
  lagEvents: number;
  eventTypes: string[] | null;
  createdAt: string;
};

export type Metrics = {
  deliveries: { pending: number; delivered: number; failed: number };
  oldestPendingAgeSeconds: number;
  eventsIngestedTotal: number;
  attempts: { accepted: number; rejected: number };
  lastReceived: { type: string; epochSeconds: number }[];
};

export function isConfigured(): boolean {
  return KEY.length > 0;
}

async function api<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    headers: { "X-API-Key": KEY },
    cache: "no-store",
  });
  if (!response.ok) {
    throw new Error(`EventCore answered ${response.status} for ${path}`);
  }
  return response.json() as Promise<T>;
}

export function listEvents(params: { type?: string; cursor?: string; limit?: number }) {
  return api<EventPage>(`/v1/events?${query(params)}`);
}

export function listDeliveries(params: { status?: string; cursor?: string; limit?: number }) {
  return api<DeliveryPage>(`/v1/deliveries?${query(params)}`);
}

export function deliveryDetail(id: string) {
  return api<DeliveryDetail>(`/v1/deliveries/${id}`);
}

export async function pullFleet() {
  const fleet = await api<{ items: PullSubscriptionStatus[] }>("/v1/pull-subscriptions");
  return fleet.items;
}

export async function redeliver(id: string): Promise<void> {
  const response = await fetch(`${BASE}/v1/deliveries/${id}/redeliver`, {
    method: "POST",
    headers: { "X-API-Key": KEY },
  });
  if (!response.ok) {
    throw new Error(`redelivery answered ${response.status}`);
  }
}

/** /metrics is Prometheus text; parse just the eventcore_* series the UI shows. */
export async function metrics(): Promise<Metrics> {
  const response = await fetch(`${BASE}/metrics`, { cache: "no-store" });
  const text = await response.text();
  const single = (name: string) => numberOf(text, new RegExp(`^${name} (.+)$`, "m"));
  const labelled = (name: string, label: string, value: string) =>
    numberOf(text, new RegExp(`^${name}\\{${label}="${value}"\\} (.+)$`, "m"));

  const lastReceived = [...text.matchAll(
    /^eventcore_event_last_received_timestamp_seconds\{type="(.+)"\} (.+)$/gm,
  )].map((match) => ({ type: match[1], epochSeconds: Number(match[2]) }));

  return {
    deliveries: {
      pending: labelled("eventcore_deliveries", "status", "pending"),
      delivered: labelled("eventcore_deliveries", "status", "delivered"),
      failed: labelled("eventcore_deliveries", "status", "failed"),
    },
    oldestPendingAgeSeconds: single("eventcore_oldest_pending_delivery_age_seconds"),
    eventsIngestedTotal: single("eventcore_events_ingested_total"),
    attempts: {
      accepted: labelled("eventcore_delivery_attempts_total", "result", "accepted"),
      rejected: labelled("eventcore_delivery_attempts_total", "result", "rejected"),
    },
    lastReceived,
  };
}

function numberOf(text: string, pattern: RegExp): number {
  const match = text.match(pattern);
  return match ? Number(match[1]) : 0;
}

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") search.set(key, String(value));
  }
  return search.toString();
}
