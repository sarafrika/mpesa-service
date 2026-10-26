"use client";

import { Inbox } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useListCallbacks } from "@/api/generated/endpoints/admin-inbox/admin-inbox";
import type { ListCallbacksParams } from "@/api/generated/model";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { SectionBoundary } from "@/components/error-boundary";
import { ErrorState } from "@/components/error-state";
import { FilterChips } from "@/components/filter-chips";
import { SearchInput } from "@/components/search-input";
import { SectionCard } from "@/components/section-card";
import { TableSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { DateRangeFilter } from "@/components/transactions/date-range-filter";
import { dateRangeParams } from "@/components/transactions/transaction-filters";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { cn } from "@/lib/utils";
import { CallbackDetailSheet } from "./callback-detail-sheet";
import { asKind, asStatusFilter, KIND_OPTIONS, STATUS_OPTIONS } from "./callback-filters";
import { CallbackSummary } from "./callback-summary";
import { CALLBACK_COLUMNS, CallbacksTable } from "./callbacks-table";

const FILTER_KEYS = ["q", "status", "kind", "from", "to"] as const;
const ALL_KINDS = "all";

/** The inbox below its header: 24 h figures, filters, the paged list and the side panel (`?cb=`). */
export function CallbacksView() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const { page, size, sort, setPage, setSize, setSort, setFilters } = usePaging();

  const urlQuery = params.get("q") ?? "";
  const status = asStatusFilter(params.get("status"));
  const kind = asKind(params.get("kind"));
  const from = params.get("from") ?? undefined;
  const to = params.get("to") ?? undefined;
  const open = params.get("cb") ?? undefined;

  // The box updates the URL after typing pauses; a URL change from elsewhere refills the box.
  const [text, setText] = useState(urlQuery);
  const debounced = useDebouncedValue(text, 350);
  const pushed = useRef(urlQuery);
  useEffect(() => {
    if (debounced.trim() === pushed.current) return;
    pushed.current = debounced.trim();
    setFilters({ q: debounced.trim() || undefined });
  }, [debounced, setFilters]);
  useEffect(() => {
    if (urlQuery !== pushed.current) {
      pushed.current = urlQuery;
      setText(urlQuery);
    }
  }, [urlQuery]);

  const query: ListCallbacksParams = {
    page,
    size,
    ...(sort ? { sort: [sort] } : {}),
    ...(status !== "any" ? { status } : {}),
    ...(kind ? { kind } : {}),
    ...(urlQuery.trim() ? { q: urlQuery.trim() } : {}),
    ...dateRangeParams(from, to),
  };
  const list = useListCallbacks(query, { query: pagedQueryOptions });
  const data = list.data?.status === 200 ? list.data.data : undefined;
  useClampPage(data, page, setPage);

  const setOpen = useCallback(
    (uuid: string | undefined) => {
      const next = new URLSearchParams(params.toString());
      if (uuid) next.set("cb", uuid);
      else next.delete("cb");
      const search = next.toString();
      router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
    },
    [params, pathname, router],
  );

  const filtered = useMemo(() => FILTER_KEYS.some((key) => params.get(key)), [params]);
  const clearFilters = () => {
    setText("");
    pushed.current = "";
    setFilters(Object.fromEntries(FILTER_KEYS.map((key) => [key, undefined])));
  };

  return (
    <>
      <SectionBoundary title="Last 24 hours">
        <CallbackSummary />
      </SectionBoundary>

      <SectionBoundary title="Filters">
        <SectionCard contentClassName="gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <SearchInput
              value={text}
              onChange={setText}
              placeholder="TransID, receipt, checkout or conversation id"
              label="Search callbacks"
              className="min-w-64 flex-1"
            />
            <Select
              value={kind ?? ALL_KINDS}
              onValueChange={(next) => setFilters({ kind: next === ALL_KINDS ? undefined : next })}
            >
              <SelectTrigger aria-label="Callback kind" className="h-9 min-w-44">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_KINDS}>All kinds</SelectItem>
                {KIND_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <DateRangeFilter
              from={from}
              to={to}
              onChange={(range) => setFilters({ from: range.from, to: range.to })}
            />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-muted-foreground text-xs">Status</span>
            <FilterChips
              label="Status"
              options={STATUS_OPTIONS}
              value={status}
              onChange={(value) => setFilters({ status: value === "any" ? undefined : value })}
            />
          </div>
        </SectionCard>
      </SectionBoundary>

      <SectionBoundary title="Callbacks">
        <SectionCard contentClassName="gap-3 px-0">
          <div className="px-4 empty:hidden">
            <StaleNotice query={list} />
          </div>
          {!data ? (
            list.isError ? (
              <ErrorState
                error={list.error}
                requiredRole="mpesa-viewer"
                onRetry={() => list.refetch()}
                retrying={list.isFetching}
              />
            ) : (
              <div className="px-4">
                <TableSkeleton rows={10} columns={CALLBACK_COLUMNS} />
              </div>
            )
          ) : data.items.length === 0 ? (
            <EmptyState
              icon={Inbox}
              title={filtered ? "No callbacks match these filters" : "No callbacks yet"}
              description={
                filtered
                  ? "Try a wider date range or another status or kind."
                  : "Every request Safaricom sends to a callback URL shows up here the moment it arrives."
              }
              action={
                filtered ? (
                  <Button variant="outline" onClick={clearFilters}>
                    Clear filters
                  </Button>
                ) : (
                  <Button asChild variant="outline">
                    <Link href="/paybills">Check paybill callback URLs</Link>
                  </Button>
                )
              }
            />
          ) : (
            <>
              <div
                className={cn("transition-opacity", list.isPlaceholderData && "opacity-60")}
                aria-busy={list.isPlaceholderData || undefined}
              >
                <CallbacksTable
                  rows={data.items}
                  sort={sort}
                  onSort={setSort}
                  selected={open}
                  onSelect={setOpen}
                />
              </div>
              <DataTablePagination
                className="px-4"
                meta={data}
                onPageChange={setPage}
                onPageSizeChange={setSize}
                disabled={list.isPlaceholderData}
              />
            </>
          )}
        </SectionCard>
      </SectionBoundary>

      <SectionBoundary title="Callback">
        <CallbackDetailSheet uuid={open} onClose={() => setOpen(undefined)} />
      </SectionBoundary>
    </>
  );
}
