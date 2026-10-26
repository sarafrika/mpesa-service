"use client";

import { RotateCw, TriangleAlert } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { formatRelative } from "@/lib/format";
import { cn } from "@/lib/utils";

type StaleQuery = {
  isRefetchError: boolean;
  isFetching: boolean;
  dataUpdatedAt: number;
  refetch: () => unknown;
};

type StaleNoticeProps = {
  /** Any React Query result; the notice shows only while a refetch failed over cached data. */
  query: StaleQuery;
  className?: string;
};

/** Keeps cached data on screen when a refresh fails, and says how old it is. */
export function StaleNotice({ query, className }: StaleNoticeProps) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!query.isRefetchError) return;
    const timer = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(timer);
  }, [query.isRefetchError]);

  if (!query.isRefetchError) return null;
  const age = formatRelative(query.dataUpdatedAt, Math.max(now, query.dataUpdatedAt));

  return (
    <div
      role="status"
      className={cn(
        "flex flex-wrap items-center gap-2 rounded-md border border-warning/30 bg-warning-soft px-3 py-1.5 text-warning text-xs",
        className,
      )}
    >
      <TriangleAlert className="size-3.5 shrink-0" aria-hidden />
      <span className="flex-1">
        Showing data from {age}
        {query.isFetching ? " — retrying…" : " — couldn't refresh"}
      </span>
      {!query.isFetching && (
        <Button
          variant="ghost"
          size="xs"
          className="h-6 text-warning hover:text-warning"
          onClick={() => query.refetch()}
        >
          <RotateCw /> Retry
        </Button>
      )}
    </div>
  );
}
