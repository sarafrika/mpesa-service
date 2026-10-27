"use client";

import { CreditCard } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { useListShortcodes } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { grantableOperations } from "@/components/paybills/capabilities";
import { TypeBadge } from "@/components/paybills/paybill-badges";
import { SearchInput } from "@/components/search-input";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Skeleton } from "@/components/ui/skeleton";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { pagedQueryOptions } from "@/hooks/use-paging";
import { formatCount } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  OPERATION_HINTS,
  OPERATION_LABELS,
  OPERATIONS,
  type Operation,
  okData,
} from "./product-format";

/** Operations per paybill uuid; a paybill with no operations is not granted. */
export type GrantSelection = Record<string, Operation[]>;

type GrantMatrixProps = {
  value: GrantSelection;
  onChange: (next: GrantSelection) => void;
  disabled?: boolean;
  /** id of the element describing a validation problem, for aria-describedby. */
  errorId?: string;
  /** id of the visible heading that names the list, for aria-labelledby. */
  labelId?: string;
};

// The service caps a page at 100; past that the matrix asks for a search instead of paging.
const LIMIT = 100;

function useShortcodePage(q: string) {
  return useListShortcodes(
    { size: LIMIT, sort: ["shortcode,asc"], ...(q ? { q } : {}) },
    { query: pagedQueryOptions },
  );
}

/** Every paybill and till as a row, with a checkbox per operation the product may use on it. */
export function GrantMatrix(props: GrantMatrixProps) {
  const [search, setSearch] = useState("");
  const q = useDebouncedValue(search.trim(), 300);
  const shortcodes = useShortcodePage(q);
  const page = okData(shortcodes.data);
  const searchable = Boolean(q) || (page?.total_items ?? 0) > LIMIT;

  return (
    <div className="flex flex-col gap-2">
      {searchable && (
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder="Search paybills by name or number"
        />
      )}
      {page && page.total_items > page.items.length && (
        <p className="text-muted-foreground text-xs" aria-live="polite">
          Showing the first {formatCount(page.items.length)} of {formatCount(page.total_items)}{" "}
          paybills; search to find the others. Ticks on paybills you can't see are kept.
        </p>
      )}
      <MatrixBody {...props} shortcodes={shortcodes} searching={Boolean(q)} />
    </div>
  );
}

function MatrixBody({
  value,
  onChange,
  disabled,
  errorId,
  labelId,
  shortcodes,
  searching,
}: GrantMatrixProps & {
  shortcodes: ReturnType<typeof useShortcodePage>;
  searching: boolean;
}) {
  const page = okData(shortcodes.data);

  if (shortcodes.isPending) {
    return (
      <div aria-busy="true" className="flex flex-col gap-2">
        <span className="sr-only">Loading paybills…</span>
        {[0, 1, 2].map((row) => (
          <Skeleton key={row} className="h-16 w-full" />
        ))}
      </div>
    );
  }
  if (shortcodes.isError && !page) {
    return (
      <ErrorState
        error={shortcodes.error}
        onRetry={() => shortcodes.refetch()}
        retrying={shortcodes.isFetching}
        className="py-4"
      />
    );
  }
  const rows = page?.items ?? [];
  if (rows.length === 0 && searching) {
    return <p className="py-4 text-center text-muted-foreground text-sm">No paybill matches.</p>;
  }
  if (rows.length === 0) {
    return (
      <EmptyState
        icon={CreditCard}
        title="No paybills or tills yet"
        description="Add a paybill first, then grant it to products."
        action={
          <Button asChild variant="outline" size="sm">
            <Link href="/paybills">Go to paybills</Link>
          </Button>
        }
        className="py-4"
      />
    );
  }

  function toggle(uuid: string, operation: Operation, on: boolean) {
    const current = new Set(value[uuid] ?? []);
    if (on) current.add(operation);
    else current.delete(operation);
    const next = { ...value };
    if (current.size === 0) delete next[uuid];
    else next[uuid] = OPERATIONS.filter((op) => current.has(op));
    onChange(next);
  }

  return (
    <ul
      className={cn(
        "flex max-h-80 flex-col gap-2 overflow-y-auto transition-opacity",
        shortcodes.isPlaceholderData && "opacity-60",
      )}
      aria-labelledby={labelId}
      aria-describedby={errorId}
      aria-busy={shortcodes.isPlaceholderData || undefined}
    >
      {rows.map((code) => {
        const chosen = value[code.uuid] ?? [];
        return (
          <li
            key={code.uuid}
            className={cn(
              "flex flex-col gap-2 rounded-lg border p-3",
              chosen.length > 0 && "border-primary/40 bg-primary/5",
            )}
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-medium font-mono text-sm tabular-nums">{code.shortcode}</span>
              <span className="min-w-0 flex-1 truncate text-sm">{code.business_name}</span>
              <TypeBadge type={code.shortcode_type} />
              {!code.is_active && <StatusBadge status="INACTIVE" />}
              {code.environment === "SANDBOX" && <StatusBadge status="SANDBOX" />}
            </div>
            <fieldset
              aria-label={`Operations on ${code.shortcode}`}
              className="m-0 flex flex-wrap gap-x-4 gap-y-2 border-0 p-0"
            >
              {OPERATIONS.filter(
                (operation) =>
                  grantableOperations(code).includes(operation) || chosen.includes(operation),
              ).map((operation) => {
                const id = `grant-${code.uuid}-${operation}`;
                return (
                  <label
                    key={operation}
                    htmlFor={id}
                    title={OPERATION_HINTS[operation]}
                    className="flex cursor-pointer items-center gap-2 text-sm"
                  >
                    <Checkbox
                      id={id}
                      checked={chosen.includes(operation)}
                      disabled={disabled}
                      onCheckedChange={(on) => toggle(code.uuid, operation, on === true)}
                    />
                    {OPERATION_LABELS[operation]}
                  </label>
                );
              })}
            </fieldset>
          </li>
        );
      })}
    </ul>
  );
}
