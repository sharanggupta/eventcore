"use server";

import { revalidatePath } from "next/cache";
import { registerWebhook, removeWebhook, type RegisteredWebhook } from "@/lib/eventcore";

export type RegisterResult =
  | { ok: true; webhook: RegisteredWebhook }
  | { ok: false; error: string }
  | null;

export async function registerAction(_previous: RegisterResult, form: FormData): Promise<RegisterResult> {
  const url = String(form.get("url") ?? "").trim();
  const eventTypes = splitList(form.get("eventTypes"));
  const payloadFields = splitList(form.get("payloadFields"));
  try {
    const webhook = await registerWebhook({ url, eventTypes, payloadFields });
    revalidatePath("/webhooks");
    return { ok: true, webhook };
  } catch (failure) {
    return { ok: false, error: failure instanceof Error ? failure.message : "registration failed" };
  }
}

export async function removeAction(id: string): Promise<void> {
  await removeWebhook(id);
  revalidatePath("/webhooks");
}

function splitList(value: FormDataEntryValue | null): string[] | undefined {
  const items = String(value ?? "").split(",").map((item) => item.trim()).filter(Boolean);
  return items.length > 0 ? items : undefined;
}
