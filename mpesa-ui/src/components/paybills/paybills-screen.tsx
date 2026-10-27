"use client";

import { CreditCard, Plus } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useListProducts } from "@/api/generated/endpoints/admin-products/admin-products";
import {
  getExportShortcodesUrl,
  useGetShortcodeTotals,
  useListShortcodes,
} from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import type {
  ListShortcodesEnvironment,
  ListShortcodesType,
  ShortcodeStatus,
} from "@/api/generated/model";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { SectionBoundary } from "@/components/error-boundary";
import { ErrorState } from "@/components/error-state";
import { ExportCsvButton } from "@/components/export-csv-button";
import { FilterChips } from "@/components/filter-chips";
import { Money } from "@/components/money";
import { PageHeader } from "@/components/page-header";
import { RoleGate } from "@/components/role-gate";
import { SearchInput } from "@/components/search-input";
import { SectionCard } from "@/components/section-card";
import { StatCardSkeleton, TableSkeleton } from "@/components/skeletons";
import { SortableHead } from "@/components/sortable-head";
import { StaleNotice } from "@/components/stale-notice";
import { StatCard } from "@/components/stat-card";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { formatCount } from "@/lib/format";
import { cn } from "@/lib/utils";
import { okData } from "./api";
import { collects, paysOut } from "./capabilities";
import {
  EnvironmentBadge,
  InlineHealthChecks,
  PaybillStatusBadge,
  TypeBadge,
} from "./paybill-badges";
import { SHORTCODE_TYPES, shortcodeNumbers, TYPE_SHORT_LABELS } from "./shortcode-types";

const TYPE_OPTIONS = [
  { value: "all", label: "All" },
  ...SHORTCODE_TYPES.map((value) => ({ value, label: TYPE_SHORT_LABELS[value] })),
];

const STATUS_OPTIONS = [
  { value: "all", label: "Any status" },
  { value: "ACTIVE", label: "Active" },
  { value: "SUSPENDED", label: "Suspended" },
  { value: "DRAFT", label: "Draft" },
] as const;

const ENVIRONMENT_OPTIONS = [
  { value: "all", label: "All environments" },
  { value: "PRODUCTION", label: "Production" },
  { value: "SANDBOX", label: "Sandbox" },
] as const;

const COLUMNS = 10;

function pick<T extends string>(value: string | null, allowed: readonly { value: string }[]) {
  return allowed.some((option) => option.value === value && value !== "all")
    ? (value as T)
    : undefined;
}

export function PaybillsScreen() {
  return (
    <>
      <PaybillsHeader />
      <SectionBoundary title="Summary">
        <PaybillTotals />
      </SectionBoundary>
      <SectionBoundary title="Paybills and tills">
        <PaybillTable />
      </SectionBoundary>
    </>
  );
}

function PaybillsHeader() {
  const totals = useGetShortcodeTotals();
  const data = okData(totals.data);
  const description = data
    ? `${formatCount(data.total)} shortcodes · ${formatCount(data.production)} production, ${formatCount(data.sandbox)} sandbox · credentials encrypted at rest`
    : "Every Sarafrika paybill, till and B2C shortcode · credentials encrypted at rest";
  return (
    <PageHeader
      title="Paybills & tills"
      description={description}
      actions={
        <div className="flex flex-wrap gap-3">
          <PaybillsExport />
          <RoleGate needs="mpesa-operator">
            <Button asChild>
              <Link href="/paybills/new">
                <Plus /> Add shortcode
              </Link>
            </Button>
          </RoleGate>
        </div>
      }
    />
  );
}

/** "Export CSV": the list under the current filters, with each shortcode's health. */
function PaybillsExport() {
  const params = useSearchParams();
  const paging = usePaging();
  return (
    <ExportCsvButton
      label="Export CSV"
      method="GET"
      fileName="paybills.csv"
      url={() =>
        getExportShortcodesUrl({
          sort: paging.sort ? [paging.sort] : undefined,
          type: pick<ListShortcodesType>(params.get("type"), TYPE_OPTIONS),
          status: pick<ShortcodeStatus>(params.get("status"), STATUS_OPTIONS),
          environment: pick<ListShortcodesEnvironment>(
            params.get("environment"),
            ENVIRONMENT_OPTIONS,
          ),
          q: params.get("q") || undefined,
          client_id: params.get("product") ?? undefined,
        })
      }
    />
  );
}

function PaybillTotals() {
  const totals = useGetShortcodeTotals();
  const data = okData(totals.data);

  if (totals.isPending) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCardSkeleton />
        <StatCardSkeleton />
        <StatCardSkeleton />
        <StatCardSkeleton />
      </div>
    );
  }
  if (!data) {
    return (
      <SectionCard title="Summary">
        <ErrorState
          error={totals.error}
          onRetry={() => totals.refetch()}
          retrying={totals.isFetching}
        />
      </SectionCard>
    );
  }
  return (
    <div className="flex flex-col gap-2">
      <StaleNotice query={totals} />
      <section aria-label="Summary" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Active"
          value={`${formatCount(data.active)} of ${formatCount(data.total)}`}
          note={
            data.draft > 0
              ? `${formatCount(data.draft)} draft, ${formatCount(data.suspended)} suspended`
              : `${formatCount(data.suspended)} suspended`
          }
        />
        <StatCard
          label="C2B URLs registered"
          value={`${formatCount(data.c2b_registered)} of ${formatCount(data.c2b_applicable)}`}
          note={
            data.c2b_registered < data.c2b_applicable
              ? "Some shortcodes still need their C2B URLs registered"
              : undefined
          }
          noteTone="warning"
        />
        <StatCard
          label="Total float"
          value={<Money amount={data.total_float} whole />}
          note={
            data.float_known > 0
              ? `From the latest payout result of ${formatCount(data.float_known)} shortcode${data.float_known === 1 ? "" : "s"}`
              : "No payout has reported a float yet"
          }
        />
        <StatCard
          label="B2C initiator errors (24 h)"
          value={formatCount(data.initiator_errors_24h)}
          note={
            data.initiator_errors_24h > 0
              ? "Payouts refused for the initiator's credentials. Update them on the shortcode's Credentials tab."
              : undefined
          }
          noteTone="warning"
        />
      </section>
    </div>
  );
}

function PaybillTable() {
  const params = useSearchParams();
  const paging = usePaging();
  const type = pick<ListShortcodesType>(params.get("type"), TYPE_OPTIONS);
  const status = pick<ShortcodeStatus>(params.get("status"), STATUS_OPTIONS);
  const environment = pick<ListShortcodesEnvironment>(
    params.get("environment"),
    ENVIRONMENT_OPTIONS,
  );
  const q = params.get("q") ?? "";
  const product = params.get("product") ?? undefined;
  const products = useListProducts({ size: 100 });
  const productOptions = okData(products.data)?.items ?? [];

  // The box updates at once; the URL (and so the query) only after typing pauses.
  // Back/forward or "Clear filters" change q from outside, so the box follows it; only typing pushes.
  const [search, setSearch] = useState(q);
  const [seenQ, setSeenQ] = useState(q);
  if (q !== seenQ) {
    setSeenQ(q);
    setSearch(q);
  }
  const debounced = useDebouncedValue(search);
  const pushed = useRef(debounced);
  const { setFilters } = paging;
  useEffect(() => {
    if (debounced === pushed.current) return;
    pushed.current = debounced;
    if (debounced.trim() !== q) setFilters({ q: debounced.trim() || undefined });
  }, [debounced, q, setFilters]);

  const query = useListShortcodes(
    {
      page: paging.page,
      size: paging.size,
      sort: paging.sort ? [paging.sort] : undefined,
      type,
      status,
      environment,
      q: q || undefined,
      client_id: product,
    },
    { query: pagedQueryOptions },
  );
  const data = okData(query.data);
  useClampPage(data, paging.page, paging.setPage);
  const [now] = useState(() => Date.now());
  const filtered = Boolean(type || status || environment || q || product);

  return (
    <Card className="gap-0 py-0">
      <div className="flex flex-wrap items-center gap-3 border-b px-4 py-4 md:px-5">
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder="Search by name or shortcode"
          className="min-w-0 flex-[1_1_240px]"
        />
        <FilterChips
          label="Type"
          options={TYPE_OPTIONS}
          value={type ?? "all"}
          onChange={(value) => setFilters({ type: value === "all" ? undefined : value })}
        />
        <Select
          value={status ?? "all"}
          onValueChange={(value) => setFilters({ status: value === "all" ? undefined : value })}
        >
          <SelectTrigger aria-label="Status" className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {STATUS_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={environment ?? "all"}
          onValueChange={(value) =>
            setFilters({ environment: value === "all" ? undefined : value })
          }
        >
          <SelectTrigger aria-label="Environment" className="w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {ENVIRONMENT_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={product ?? "all"}
          onValueChange={(value) => setFilters({ product: value === "all" ? undefined : value })}
        >
          <SelectTrigger aria-label="Product" className="w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All products</SelectItem>
            {product && !productOptions.some((option) => option.client_id === product) && (
              <SelectItem value={product}>{product}</SelectItem>
            )}
            {productOptions.map((option) => (
              <SelectItem key={option.client_id} value={option.client_id}>
                {option.display_name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {data && <StaleNotice query={query} className="mx-4 mt-3 md:mx-5" />}

      {query.isPending ? (
        <TableSkeleton columns={COLUMNS} rows={5} className="m-4 md:m-5" />
      ) : !data ? (
        <ErrorState
          error={query.error}
          resource="shortcode list"
          onRetry={() => query.refetch()}
          retrying={query.isFetching}
        />
      ) : data.items.length === 0 ? (
        filtered ? (
          <EmptyState
            title="No shortcodes match"
            description="Try another name or number, or clear the filters."
            action={
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setSearch("");
                  setFilters({
                    type: undefined,
                    status: undefined,
                    environment: undefined,
                    q: undefined,
                    product: undefined,
                  });
                }}
              >
                Clear filters
              </Button>
            }
          />
        ) : (
          <EmptyState
            icon={CreditCard}
            title="No shortcodes yet"
            description="Add Sarafrika's first paybill, till or B2C shortcode to collect or pay out through the console."
            action={
              <RoleGate needs="mpesa-operator">
                <Button size="sm" asChild>
                  <Link href="/paybills/new">
                    <Plus /> Add shortcode
                  </Link>
                </Button>
              </RoleGate>
            }
          />
        )
      ) : (
        <div
          className={cn(
            "overflow-x-auto transition-opacity",
            query.isPlaceholderData && "opacity-60",
          )}
          aria-busy={query.isPlaceholderData || undefined}
        >
          <Table>
            <TableHeader>
              <TableRow className="bg-muted/40">
                <SortableHead
                  field="business_name"
                  defaultField="business_name"
                  sort={paging.sort}
                  onSort={paging.setSort}
                  className="pl-4 md:pl-5"
                >
                  Shortcode
                </SortableHead>
                <TableHead>Type</TableHead>
                <TableHead>Product</TableHead>
                <TableHead>Env</TableHead>
                <TableHead>Checks</TableHead>
                <TableHead className="text-right">Today (KES)</TableHead>
                <TableHead className="text-right">Float (KES)</TableHead>
                <SortableHead
                  field="created_at"
                  sort={paging.sort}
                  onSort={paging.setSort}
                  className="hidden 2xl:table-cell"
                >
                  Added
                </SortableHead>
                <TableHead>Status</TableHead>
                <TableHead>
                  <span className="sr-only">Actions</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.items.map((row) => (
                <TableRow key={row.uuid}>
                  <TableCell className="pl-4 md:pl-5">
                    <Link
                      href={`/paybills/${row.uuid}`}
                      className="flex flex-col gap-0.5 hover:underline"
                    >
                      <span className="font-semibold">{row.business_name}</span>
                      <span className="text-muted-foreground text-xs tabular-nums">
                        {shortcodeNumbers(row)}
                      </span>
                    </Link>
                  </TableCell>
                  <TableCell>
                    <TypeBadge type={row.shortcode_type} />
                  </TableCell>
                  <TableCell className="min-w-28 max-w-44 whitespace-normal">
                    {row.products.length === 0 ? (
                      <span className="text-muted-foreground">Unassigned</span>
                    ) : (
                      row.products.map((p) => p.display_name || p.client_id).join(", ")
                    )}
                  </TableCell>
                  <TableCell>
                    <EnvironmentBadge environment={row.environment} />
                  </TableCell>
                  <TableCell>
                    <InlineHealthChecks row={row} now={now} />
                  </TableCell>
                  <TableCell className="text-right font-semibold">
                    {paysOut(row) && !collects(row) ? (
                      <Money amount={row.health.paid_out_today} direction="out" hideCode />
                    ) : (
                      <Money amount={row.health.collected_today} hideCode />
                    )}
                  </TableCell>
                  <TableCell className="text-right font-semibold">
                    <Money amount={row.health.float_utility} hideCode />
                  </TableCell>
                  <TableCell className="hidden text-muted-foreground tabular-nums 2xl:table-cell">
                    {row.created_at
                      ? new Date(row.created_at).toLocaleDateString("en-KE", {
                          dateStyle: "medium",
                        })
                      : "—"}
                  </TableCell>
                  <TableCell>
                    <PaybillStatusBadge row={row} now={now} />
                  </TableCell>
                  <TableCell className="pr-4 md:pr-5">
                    <Button variant="outline" size="sm" asChild>
                      <Link
                        href={`/paybills/${row.uuid}`}
                        aria-label={`Manage ${row.business_name}`}
                      >
                        Manage
                      </Link>
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {data && data.items.length > 0 && (
        <div className="flex flex-col gap-2 border-t px-4 py-3 md:px-5">
          <DataTablePagination
            meta={data}
            onPageChange={paging.setPage}
            onPageSizeChange={paging.setSize}
            disabled={query.isPlaceholderData}
          />
          <span className="text-muted-foreground text-xs">
            Checks: Daraja token · C2B URLs · last payment or payout · callbacks
          </span>
        </div>
      )}
    </Card>
  );
}
