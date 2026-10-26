"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";

// A backfill adds a transaction and closes an attention item, so those lists refresh too.
const PREFIXES = [
  "/api/v1/admin/reconciliation",
  "/api/v1/admin/transactions",
  "/api/v1/admin/metrics",
];

/** Refetches every reconciliation query (runs, summaries, exceptions) after a run or a resolve action. */
export function useRefreshReconciliation() {
  const queryClient = useQueryClient();
  return useCallback(
    () =>
      queryClient.invalidateQueries({
        predicate: (query) =>
          typeof query.queryKey[0] === "string" &&
          PREFIXES.some((prefix) => String(query.queryKey[0]).startsWith(prefix)),
      }),
    [queryClient],
  );
}
