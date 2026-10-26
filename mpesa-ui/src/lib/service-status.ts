// Whether the last proxied call reached mpesa-service; the header pill reads this.
export type ServiceStatus = "unknown" | "up" | "down";

/** Header the proxy sets on its 503 when mpesa-service did not answer at all. */
export const UNREACHABLE_HEADER = "x-mpesa-unreachable";

let status: ServiceStatus = "unknown";
const listeners = new Set<() => void>();

export function setServiceStatus(next: ServiceStatus) {
  if (next === status) return;
  status = next;
  for (const listener of listeners) listener();
}

export function getServiceStatus(): ServiceStatus {
  return status;
}

export function subscribeServiceStatus(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
