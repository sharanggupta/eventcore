"use server";

import { revalidatePath } from "next/cache";
import { redeliver, redeliverAllFailed } from "@/lib/eventcore";

export async function redeliverAllFailedAction(): Promise<void> {
  await redeliverAllFailed();
  revalidatePath("/deliveries");
}

export async function redeliverAction(id: string): Promise<void> {
  await redeliver(id);
  revalidatePath("/deliveries");
}
