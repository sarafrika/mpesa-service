"use client";

import { ArrowLeftRight } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useListShortcodes } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import { useListTransactions } from "@/api/generated/endpoints/admin-transactions/admin-transactions";
import type { ListTransactionsParams } from "@/api/generated/model";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { SectionBoundary } from "@/components/error-boundary";
import { ErrorState } from "@/components/error-state";
import { FilterChips } from "@/components/filter-chips";
import { SearchInput } from "@/components/search-input";
import { SectionCard } from "@/components/section-card";
import { TableSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { Button } from "@/components/ui/button";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { cn } from "@/lib/utils";
import { DateRangeFilter } from "./date-range-filter";
import { type PaybillOption, PaybillSelect } from "./paybill-select";
import { TransactionDetailSheet } from "./transaction-detail-sheet";
import {
  asStatusFilter,
  asTypeFilter,
  dateRangeParams,
  STATUS_OPTIONS,
  searchParams,
  TYPE_OPTIONS,
} from "./transaction-filters";
import { TABLE_COLUMNS, TransactionsTable } from "./transactions-table";

const FILTER_KEYS = ["q", "type", "status", "paybill", "from", "to"] as const;

/** The transactions screen below its header: filters, the paged feed and the side panel (`?tx=`). */
export function TransactionsView() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const paging = usePaging();
  const { page, size, sort, setPage, setSize, setSort, setFilters } = paging;

  const urlQuery = params.get("q") ?? "";
  const type = asTypeFilter(params.get("type"));
  const status = asStatusFilter(params.get("status"));
  const paybill = params.get("paybill") ?? undefined;
  const from = params.get("from") ?? undefined;
  const to = params.get("to") ?? undefined;
  const tx = params.get("tx") ?? undefined;

  // The box updates the URL after typing pauses; a URL change from elsewhere (back, a link) refills the box.
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

  const query: ListTransactionsParams = {
    page,
    size,
    ...(sort ? { sort: [sort] } : {}),
    ...(type !== "all" ? { type } : {}),
    ...(status !== "any" ? { status } : {}),
    ...(paybill ? { shortcode_uuid: paybill } : {}),
    ...searchParams(urlQuery),
    ...dateRangeParams(from, to),
  };
  const list = useListTransactions(query, { query: pagedQueryOptions });
  const data = list.data?.status === 200 ? list.data.data : undefined;
  useClampPage(data, page, setPage);

  const shortcodes = useListShortcodes({ size: 100, sort: ["business_name,asc"] });
  const paybills = useMemo<PaybillOption[]>(
    () =>
      shortcodes.data?.status === 200
        ? shortcodes.data.data.items.map((code) => ({
            uuid: code.uuid,
            label: `${code.business_name} · ${code.shortcode}`,
          }))
        : [],
    [shortcodes.data],
  );

  const setTx = useCallback(
    (uuid: string | undefined) => {
      const next = new URLSearchParams(params.toString());
      if (uuid) next.set("tx", uuid);
      else next.delete("tx");
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
      <SectionBoundary title="Filters">
        <SectionCard contentClassName="gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <SearchInput
              value={text}
              onChange={setText}
              placeholder="M-Pesa receipt, account reference, phone or checkout id"
              label="Search transactions"
              className="min-w-64 flex-1"
            />
            <PaybillSelect
              options={paybills}
              value={paybill}
              onChange={(uuid) => setFilters({ paybill: uuid })}
            />
            <DateRangeFilter
              from={from}
              to={to}
              onChange={(range) => setFilters({ from: range.from, to: range.to })}
            />
          </div>
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-muted-foreground text-xs">Type</span>
              <FilterChips
                label="Transaction type"
                options={TYPE_OPTIONS}
                value={type}
                onChange={(value) => setFilters({ type: value === "all" ? undefined : value })}
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
          </div>
        </SectionCard>
      </SectionBoundary>

      <SectionBoundary title="Transactions">
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
                <TableSkeleton rows={10} columns={TABLE_COLUMNS} />
              </div>
            )
          ) : data.items.length === 0 ? (
            <EmptyState
              icon={ArrowLeftRight}
              title={filtered ? "No transactions match these filters" : "No transactions yet"}
              description={
                filtered
                  ? "Try a wider date range or another type or status."
                  : "Deposits, STK requests and payouts appear here as soon as Safaricom reports them."
              }
              action={
                filtered ? (
                  <Button variant="outline" onClick={clearFilters}>
                    Clear filters
                  </Button>
                ) : (
                  <Button asChild variant="outline">
                    <Link href="/paybills">Check your paybills</Link>
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
                <TransactionsTable
                  rows={data.items}
                  sort={sort}
                  onSort={setSort}
                  selected={tx}
                  onSelect={setTx}
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

      <SectionBoundary title="Transaction">
        <TransactionDetailSheet uuid={tx} onClose={() => setTx(undefined)} />
      </SectionBoundary>
    </>
  );
}
