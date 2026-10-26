import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { toast } from "sonner";
import { type DescribeOptions, describeApiError, isApiError } from "@/lib/api-errors";

type Response = { status: number; data: unknown };

/** mpesaFetch throws on every non-2xx, so a resolved response always carries the success body. */
export function okData<T extends Response>(response: T | undefined) {
  return response?.data as Extract<T, { status: 200 | 201 }>["data"] | undefined;
}

/** Refetches every shortcode query (list, totals, detail, C2B history) after a change. */
export function useInvalidateShortcodes() {
  const queryClient = useQueryClient();
  return useCallback(
    () =>
      queryClient.invalidateQueries({
        predicate: (query) =>
          typeof query.queryKey[0] === "string" &&
          query.queryKey[0].startsWith("/api/v1/admin/shortcodes"),
      }),
    [queryClient],
  );
}

/** The toast a mutation gets when the form has nowhere to show the error inline. */
export function toastApiError(error: unknown, options?: DescribeOptions) {
  const copy = describeApiError(error, options);
  if (copy.kind === "unauthorized") return;
  toast.error(copy.title, { description: upstreamMessage(error) ?? copy.description });
}

/** For 502/503 the service says what Safaricom did, which beats the generic "unavailable" copy. */
export function upstreamMessage(error: unknown): string | undefined {
  return isApiError(error) && (error.status === 502 || error.status === 503)
    ? error.serverMessage
    : undefined;
}
