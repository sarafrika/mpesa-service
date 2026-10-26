"use client";

import { TriangleAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { describeApiError } from "@/lib/api-errors";

type OptionsQuery = {
  isError: boolean;
  data?: unknown;
  error: unknown;
  isFetching: boolean;
  refetch: () => unknown;
};

/** Beside a select whose options failed to load, so it does not just look empty. */
export function OptionsError({ what, query }: { what: string; query: OptionsQuery }) {
  if (!query.isError || query.data !== undefined) return null;
  const copy = describeApiError(query.error);
  return (
    <span
      role="alert"
      className="inline-flex flex-wrap items-center gap-1.5 text-destructive text-xs"
    >
      <TriangleAlert className="size-3.5 shrink-0" aria-hidden />
      <span title={copy.description}>Could not load {what}.</span>
      {copy.retryable && (
        <Button
          variant="link"
          size="sm"
          className="h-auto p-0 text-xs"
          disabled={query.isFetching}
          onClick={() => query.refetch()}
        >
          {query.isFetching ? "Retrying…" : "Retry"}
        </Button>
      )}
    </span>
  );
}
