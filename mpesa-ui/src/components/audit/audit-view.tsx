"use client";

import { ScrollText } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useListAuditEvents } from "@/api/generated/endpoints/admin-audit/admin-audit";
import type { ListAuditEventsParams } from "@/api/generated/model";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { SectionBoundary } from "@/components/error-boundary";
import { ErrorState } from "@/components/error-state";
import { SectionCard } from "@/components/section-card";
import { TableSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { cn } from "@/lib/utils";
import { AuditDetailSheet } from "./audit-detail-sheet";
import { AUDIT_COLUMNS, AuditTable } from "./audit-table";

const FILTERS = [
  { key: "actor", label: "Actor", placeholder: "jane.doe" },
  { key: "action", label: "Action", placeholder: "SHORTCODE_UPDATED", upper: true },
  { key: "entity_type", label: "Entity type", placeholder: "SHORTCODE", upper: true },
  { key: "entity_id", label: "Entity id", placeholder: "uuid or id" },
] as const;

type FilterKey = (typeof FILTERS)[number]["key"];

/** The audit trail below its header: exact-match filters, the paged list and the diff panel (`?event=`). */
export function AuditView() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const { page, size, sort, setPage, setSize, setSort, setFilters } = usePaging();
  const open = params.get("event") ?? undefined;

  const values = Object.fromEntries(
    FILTERS.map((filter) => [filter.key, params.get(filter.key)?.trim() || undefined]),
  ) as Record<FilterKey, string | undefined>;
  const query: ListAuditEventsParams = {
    page,
    size,
    ...(sort ? { sort: [sort] } : {}),
    ...Object.fromEntries(Object.entries(values).filter(([, value]) => value)),
  };
  const list = useListAuditEvents(query, { query: pagedQueryOptions });
  const data = list.data?.status === 200 ? list.data.data : undefined;
  useClampPage(data, page, setPage);
  const selected = data?.items.find((event) => event.uuid === open);

  const setOpen = useCallback(
    (uuid: string | undefined) => {
      const next = new URLSearchParams(params.toString());
      if (uuid) next.set("event", uuid);
      else next.delete("event");
      const search = next.toString();
      router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
    },
    [params, pathname, router],
  );

  const filtered = useMemo(() => FILTERS.some((filter) => params.get(filter.key)), [params]);
  const clearFilters = () =>
    setFilters(Object.fromEntries(FILTERS.map((filter) => [filter.key, undefined])));

  return (
    <>
      <SectionBoundary title="Filters">
        <SectionCard contentClassName="gap-3">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {FILTERS.map((filter) => (
              <TextFilter
                key={filter.key}
                name={filter.key}
                label={filter.label}
                placeholder={filter.placeholder}
                upper={"upper" in filter && filter.upper}
                value={values[filter.key] ?? ""}
                onCommit={(value) => setFilters({ [filter.key]: value || undefined })}
              />
            ))}
          </div>
          <p className="text-muted-foreground text-xs">
            Each filter matches its value exactly. Open an entry to see what changed.
          </p>
        </SectionCard>
      </SectionBoundary>

      <SectionBoundary title="Audit events">
        <SectionCard contentClassName="gap-3 px-0">
          <div className="px-4 empty:hidden">
            <StaleNotice query={list} />
          </div>
          {!data ? (
            list.isError ? (
              <ErrorState
                error={list.error}
                requiredRole="mpesa-admin"
                onRetry={() => list.refetch()}
                retrying={list.isFetching}
              />
            ) : (
              <div className="px-4">
                <TableSkeleton rows={10} columns={AUDIT_COLUMNS} />
              </div>
            )
          ) : data.items.length === 0 ? (
            <EmptyState
              icon={ScrollText}
              title={filtered ? "No audit events match these filters" : "No audit events yet"}
              description={
                filtered
                  ? "Filters match exactly; check the spelling or clear them."
                  : "Every change made through the console is recorded here with who made it."
              }
              action={
                filtered ? (
                  <Button variant="outline" onClick={clearFilters}>
                    Clear filters
                  </Button>
                ) : undefined
              }
            />
          ) : (
            <>
              <div
                className={cn("transition-opacity", list.isPlaceholderData && "opacity-60")}
                aria-busy={list.isPlaceholderData || undefined}
              >
                <AuditTable
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

      <SectionBoundary title="Audit event">
        <AuditDetailSheet
          event={selected}
          open={Boolean(open)}
          loading={!data && !list.isError}
          onClose={() => setOpen(undefined)}
        />
      </SectionBoundary>
    </>
  );
}

type TextFilterProps = {
  name: string;
  label: string;
  placeholder: string;
  upper?: boolean;
  value: string;
  onCommit: (value: string) => void;
};

/** A text box that updates the URL after typing pauses and follows URL changes from elsewhere. */
function TextFilter({ name, label, placeholder, upper, value, onCommit }: TextFilterProps) {
  const [text, setText] = useState(value);
  const debounced = useDebouncedValue(text, 400);
  const pushed = useRef(value);
  useEffect(() => {
    const next = debounced.trim();
    if (next === pushed.current) return;
    pushed.current = next;
    onCommit(next);
  }, [debounced, onCommit]);
  useEffect(() => {
    if (value !== pushed.current) {
      pushed.current = value;
      setText(value);
    }
  }, [value]);
  const id = `audit-filter-${name}`;
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id} className="text-muted-foreground text-xs">
        {label}
      </Label>
      <Input
        id={id}
        value={text}
        placeholder={placeholder}
        className="font-mono text-[13px]"
        autoComplete="off"
        spellCheck={false}
        onChange={(event) => setText(upper ? event.target.value.toUpperCase() : event.target.value)}
      />
    </div>
  );
}
