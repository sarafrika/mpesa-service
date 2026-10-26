"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";

const PREFIXES = [
  "/api/v1/admin/products",
  "/api/v1/admin/product-grants",
  "/api/v1/admin/webhooks",
];

/** Refetches every product, grant and webhook query after a change, whatever its params. */
export function useRefreshProducts() {
  const queryClient = useQueryClient();
  return useCallback(
    () =>
      queryClient.invalidateQueries({
        predicate: (query) => {
          const head = query.queryKey[0];
          return typeof head === "string" && PREFIXES.some((prefix) => head.startsWith(prefix));
        },
      }),
    [queryClient],
  );
}
