import { ApiError } from "@/lib/api-errors";
import { setServiceStatus, UNREACHABLE_HEADER } from "@/lib/service-status";

export { ApiError };

// Orval's hooks call this; every request goes through the Next.js proxy, which adds the user's token.
export const PROXY_PREFIX = "/api/mpesa";

// Error bodies are usually ApiError JSON, but a gateway page or a bad body must not crash the read.
async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const text = await response.text().catch(() => "");
  if (!text) return undefined;
  if (!response.headers.get("content-type")?.includes("json")) return text;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export async function mpesaFetch<T>(url: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${PROXY_PREFIX}${url}`, { ...options, credentials: "same-origin" });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === "AbortError") throw cause;
    throw new ApiError(0, undefined);
  }
  // A 401 comes from the proxy itself, so it says nothing about mpesa-service.
  if (response.headers.has(UNREACHABLE_HEADER)) setServiceStatus("down");
  else if (response.status !== 401) setServiceStatus("up");

  const data = await readBody(response);
  if (response.status === 401) {
    window.location.assign(`/sign-in?callbackUrl=${encodeURIComponent(window.location.href)}`);
  }
  if (!response.ok) {
    throw new ApiError(response.status, data);
  }
  return { data, status: response.status, headers: response.headers } as T;
}
