"use client";

import { FileSpreadsheet } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useMemo } from "react";
import { useListReconciliationRuns } from "@/api/generated/endpoints/admin-reconciliation/admin-reconciliation";
import { useListShortcodes } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import type { ReconciliationRunDto } from "@/api/generated/model";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { SectionBoundary } from "@/components/error-boundary";
import { ErrorState } from "@/components/error-state";
import { OptionsError } from "@/components/options-error";
import { PageHeader } from "@/components/page-header";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { TableSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { PaybillSelect } from "@/components/transactions/paybill-select";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { cn } from "@/lib/utils";
import { ExceptionsSection } from "./exceptions-section";
import { PullTransactionsAction } from "./pull-transactions-action";
import { asKind, asStatusFilter } from "./reconciliation-format";
import { RunSummary } from "./run-summary";
import { RUN_COLUMNS, RunsTable } from "./runs-table";
import { UploadStatementDialog } from "./upload-statement-dialog";

const EXCEPTION_PAGE_KEYS = ["exceptions_page", "exceptions_size", "exceptions_sort"];

/** The reconciliation page: paybill and actions in the header, the chosen run (`?run=`, else the newest),
 * its exceptions, and every run below. Filters and both tables' paging live in the URL. */
export function ReconciliationScreen() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const paybill = params.get("paybill") ?? undefined;
  const kind = asKind(params.get("kind"));
  const status = asStatusFilter(params.get("status"));
  const runs = usePaging({ prefix: "runs" });

  const shortcodes = useListShortcodes({ size: 100, sort: ["business_name,asc"] });
  const options = useMemo(
    () =>
      shortcodes.data?.status === 200
        ? shortcodes.data.data.items.map((code) => ({
            uuid: code.uuid,
            label: `${code.business_name} · ${code.shortcode}`,
          }))
        : [],
    [shortcodes.data],
  );

  const list = useListReconciliationRuns(
    {
      page: runs.page,
      size: runs.size,
      ...(runs.sort ? { sort: [runs.sort] } : {}),
      ...(paybill ? { shortcode_uuid: paybill } : {}),
    },
    { query: pagedQueryOptions },
  );
  const data = list.data?.status === 200 ? list.data.data : undefined;
  useClampPage(data, runs.page, runs.setPage);
  const chosen =
    params.get("run") ?? (runs.page === 0 && !runs.sort ? data?.items[0]?.uuid : undefined);

  // One navigation for every URL change, so filters, run and paging never race each other.
  const navigate = useCallback(
    (changes: Record<string, string | undefined>, resetExceptions = true) => {
      const next = new URLSearchParams(params.toString());
      for (const [key, value] of Object.entries(changes)) {
        if (value === undefined || value === "") next.delete(key);
        else next.set(key, value);
      }
      if (resetExceptions) for (const key of EXCEPTION_PAGE_KEYS) next.delete(key);
      const search = next.toString();
      router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
    },
    [params, pathname, router],
  );

  const showRun = (run: ReconciliationRunDto) =>
    navigate({ run: run.uuid, paybill: paybill ? run.shortcode_uuid : undefined });

  return (
    <>
      <PageHeader
        title="Reconciliation"
        description="Our ledger against Safaricom's organisation statement, matched on the M-Pesa receipt number."
        actions={
          <>
            <PaybillSelect
              options={options}
              value={paybill}
              onChange={(uuid) => navigate({ paybill: uuid, run: undefined, runs_page: undefined })}
            />
            <OptionsError what="the paybill list" query={shortcodes} />
            <RoleGate needs="mpesa-finance">
              <UploadStatementDialog paybills={options} paybill={paybill} onDone={showRun} />
            </RoleGate>
            <PullTransactionsAction paybill={paybill} onDone={showRun} />
          </>
        }
      />

      {chosen && (
        <>
          <SectionBoundary title="Run summary">
            <RunSummary
              runUuid={chosen}
              activeKind={kind}
              onKind={(next) => navigate({ kind: next === kind ? undefined : next })}
            />
          </SectionBoundary>
          <SectionBoundary title="Exceptions">
            <ExceptionsSection
              runUuid={chosen}
              kind={kind}
              status={status}
              onFilters={(filters) => navigate(filters)}
            />
          </SectionBoundary>
        </>
      )}

      <SectionBoundary title="Runs">
        <SectionCard title="Runs" contentClassName="gap-3 px-0">
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
                <TableSkeleton rows={5} columns={RUN_COLUMNS} />
              </div>
            )
          ) : data.items.length === 0 ? (
            <EmptyState
              icon={FileSpreadsheet}
              title={
                paybill ? "This paybill has not been reconciled yet" : "No reconciliation runs yet"
              }
              description="Upload the organisation statement exported from the M-Pesa org portal, or run with Pull Transactions where Safaricom has enabled it."
              action={
                <RoleGate needs="mpesa-finance">
                  <UploadStatementDialog paybills={options} paybill={paybill} onDone={showRun} />
                </RoleGate>
              }
            />
          ) : (
            <>
              <div
                className={cn("transition-opacity", list.isPlaceholderData && "opacity-60")}
                aria-busy={list.isPlaceholderData || undefined}
              >
                <RunsTable
                  rows={data.items}
                  sort={runs.sort}
                  onSort={runs.setSort}
                  selected={chosen}
                  onSelect={(uuid) => navigate({ run: uuid })}
                />
              </div>
              <DataTablePagination
                className="px-4"
                meta={data}
                onPageChange={runs.setPage}
                onPageSizeChange={runs.setSize}
                disabled={list.isPlaceholderData}
              />
            </>
          )}
        </SectionCard>
      </SectionBoundary>
    </>
  );
}
