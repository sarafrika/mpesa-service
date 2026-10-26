// Orval's hooks call this; every request goes through the Next.js proxy, which adds the user's token.
export const PROXY_PREFIX = "/api/mpesa";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: unknown,
  ) {
    super(`mpesa-service responded ${status}`);
  }
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const text = await response.text();
  if (!text) return undefined;
  return response.headers.get("content-type")?.includes("json") ? JSON.parse(text) : text;
}

export async function mpesaFetch<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${PROXY_PREFIX}${url}`, { ...options, credentials: "same-origin" });
  const data = await readBody(response);
  if (response.status === 401) {
    window.location.assign(`/sign-in?callbackUrl=${encodeURIComponent(window.location.href)}`);
  }
  if (!response.ok) {
    throw new ApiError(response.status, data);
  }
  return { data, status: response.status, headers: response.headers } as T;
}
