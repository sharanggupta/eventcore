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
  position: string; // "beginning" or an ISO-8601 timestamp
  lagEvents: number;
  eventTypes: string[]; // ["*"] means all types
  createdAt: string;
};

export type Webhook = {
  id: string;
  createdAt: string;
  url: string;
  eventTypes: string[]; // ["*"] means all event types
  payloadFields: string[]; // ["*"] means the full payload
};

export type RegisteredWebhook = Webhook & { secret: string };

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

/** Every error EventCore returns has this shape (see the backend's ApiError). */
type ApiError = { error?: string };

/** The single request boundary: reads and writes, one place that turns a non-2xx into the API's own error. */
async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    cache: "no-store",
    ...init,
    headers: {
      "X-API-Key": KEY,
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as ApiError;
    throw new Error(body.error ?? `EventCore answered ${response.status} for ${path}`);
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function listEvents(params: {
  type?: string; cursor?: string; limit?: number; from?: string; to?: string;
  payloadField?: string; payloadValue?: string;
}) {
  const { payloadField, payloadValue, ...rest } = params;
  const search = new URLSearchParams(query(rest));
  if (payloadField && payloadValue) search.set(`payload.${payloadField}`, payloadValue);
  return api<EventPage>(`/v1/events?${search}`);
}

export function listDeliveries(params: { status?: string; cursor?: string; limit?: number; from?: string; to?: string }) {
  return api<DeliveryPage>(`/v1/deliveries?${query(params)}`);
}

export function deliveryDetail(id: string) {
  return api<DeliveryDetail>(`/v1/deliveries/${id}`);
}

export async function pullFleet() {
  const fleet = await api<{ items: PullSubscriptionStatus[] }>("/v1/pull-subscriptions");
  return fleet.items;
}

export function listWebhooks() {
  return api<Webhook[]>("/v1/webhooks");
}

export function registerWebhook(input: {
  url: string;
  eventTypes?: string[];
  payloadFields?: string[];
}): Promise<RegisteredWebhook> {
  return api<RegisteredWebhook>("/v1/webhooks", { method: "POST", body: JSON.stringify(input) });
}

export function updateWebhookFilters(id: string, input: {
  eventTypes?: string[];
  payloadFields?: string[];
}): Promise<void> {
  return api<void>(`/v1/webhooks/${id}`, { method: "PATCH", body: JSON.stringify(input) });
}

export function removeWebhook(id: string): Promise<void> {
  return api<void>(`/v1/webhooks/${id}`, { method: "DELETE" });
}

export function redeliverAllFailed(): Promise<void> {
  return api<void>("/v1/deliveries/redeliver", {
    method: "POST",
    body: JSON.stringify({ status: "failed" }),
  });
}

export function redeliver(id: string): Promise<void> {
  return api<void>(`/v1/deliveries/${id}/redeliver`, { method: "POST" });
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

/** Kibana-style presets: resolve ?since=15m|1h|24h|7d (or explicit from/to) to API bounds. */
export const SINCE_PRESETS = ["15m", "1h", "24h", "7d"] as const;

const SINCE_MINUTES: Record<string, number> = { "15m": 15, "1h": 60, "24h": 1440, "7d": 10080 };

export function resolveTimeRange(params: { since?: string; from?: string; to?: string }): {
  from?: string; to?: string;
} {
  if (params.from || params.to) return { from: params.from, to: params.to };
  const minutes = params.since ? SINCE_MINUTES[params.since] : undefined;
  return minutes ? { from: new Date(Date.now() - minutes * 60_000).toISOString() } : {};
}
