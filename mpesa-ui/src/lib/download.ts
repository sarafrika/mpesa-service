import { PROXY_PREFIX } from "@/api/fetcher";
import { ApiError } from "@/lib/api-errors";
import { fileNameFrom } from "@/lib/content-disposition";

export type CsvDownload = { total: number | null; truncated: boolean };

/** Fetches a CSV export through the proxy and hands it to the browser as a download. */
export async function downloadCsv(
  url: string,
  method: "GET" | "POST",
  fallbackName: string,
): Promise<CsvDownload> {
  let response: Response;
  try {
    response = await fetch(`${PROXY_PREFIX}${url}`, { method, credentials: "same-origin" });
  } catch {
    throw new ApiError(0, undefined);
  }
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    let body: unknown = text || undefined;
    try {
      body = text ? JSON.parse(text) : undefined;
    } catch {
      // Not JSON: keep the text.
    }
    throw new ApiError(response.status, body);
  }
  const blob = await response.blob();
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = fileNameFrom(response.headers.get("content-disposition"), fallbackName);
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(href), 1000);
  const total = Number(response.headers.get("x-export-total"));
  return {
    total: Number.isFinite(total) && response.headers.has("x-export-total") ? total : null,
    truncated: response.headers.get("x-export-truncated") === "true",
  };
}
