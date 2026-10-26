"use client";

import { History } from "lucide-react";
import { useListShortcodeC2bRegistrations } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { KeyValueList } from "@/components/key-value-list";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { TableSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { okData } from "../api";
import { RegisterC2bButton, SimulateC2bDialog } from "../c2b-actions";
import type { PaybillSectionProps } from "./types";

const STATE = {
  REGISTERED: { label: "Registered", tone: "success" },
  FAILED: { label: "Failed", tone: "danger" },
  NOT_REGISTERED: { label: "Not registered", tone: "warning" },
  NOT_APPLICABLE: { label: "Not applicable", tone: "neutral" },
} as const;

export function C2bSection({ shortcode }: PaybillSectionProps) {
  const paging = usePaging({ size: 10 });
  const history = useListShortcodeC2bRegistrations(
    shortcode.uuid,
    { page: paging.page, size: paging.size, sort: paging.sort ? [paging.sort] : undefined },
    { query: pagedQueryOptions },
  );
  const data = okData(history.data);
  useClampPage(data, paging.page, paging.setPage);
  const latest = paging.page === 0 && !paging.sort ? data?.items[0] : undefined;
  const state = STATE[shortcode.health.c2b_state];

  return (
    <div className="grid gap-4">
      <SectionCard
        title="C2B registration"
        description="Where Safaricom sends validations and confirmations for payments to this shortcode."
        actions={
          <RoleGate needs="mpesa-operator">
            {shortcode.environment === "SANDBOX" && <SimulateC2bDialog shortcode={shortcode} />}
            <RegisterC2bButton
              shortcode={shortcode}
              initial={latest?.response_type ?? "Cancelled"}
            />
          </RoleGate>
        }
      >
        <KeyValueList
          items={[
            { label: "Status", value: <StatusBadge status={state.label} tone={state.tone} /> },
            {
              label: "Response type",
              value: latest
                ? latest.response_type === "Cancelled"
                  ? "Cancelled (reject when validation is unreachable)"
                  : "Completed (accept when validation is unreachable)"
                : undefined,
            },
            { label: "Confirmation URL", value: shortcode.confirmation_url, mono: true },
            { label: "Validation URL", value: shortcode.validation_url, mono: true },
            {
              label: "Last attempt",
              value: shortcode.health.c2b_registered_at
                ? `${formatDateTime(shortcode.health.c2b_registered_at)}${latest ? ` by ${latest.registered_by}` : ""}`
                : "Never",
            },
          ]}
        />
      </SectionCard>

      <SectionCard title="Registration history" description="Every attempt, newest first.">
        {data && <StaleNotice query={history} />}
        {history.isPending ? (
          <TableSkeleton columns={5} rows={3} />
        ) : !data ? (
          <ErrorState
            error={history.error}
            onRetry={() => history.refetch()}
            retrying={history.isFetching}
          />
        ) : data.items.length === 0 ? (
          <EmptyState
            icon={History}
            title="No registrations yet"
            description="Register the C2B URLs so Safaricom sends payments for this shortcode here."
          />
        ) : (
          <>
            <div
              className={cn(
                "overflow-x-auto rounded-lg border transition-opacity",
                history.isPlaceholderData && "opacity-60",
              )}
            >
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>When</TableHead>
                    <TableHead>Outcome</TableHead>
                    <TableHead>Response type</TableHead>
                    <TableHead>Safaricom said</TableHead>
                    <TableHead>By</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((item) => (
                    <TableRow key={item.uuid}>
                      <TableCell className="text-muted-foreground">
                        {formatDateTime(item.created_at)}
                      </TableCell>
                      <TableCell>
                        <StatusBadge
                          status={item.successful ? "Registered" : "Failed"}
                          tone={item.successful ? "success" : "danger"}
                        />
                      </TableCell>
                      <TableCell>{item.response_type}</TableCell>
                      <TableCell
                        className="max-w-80 truncate"
                        title={item.result_desc ?? undefined}
                      >
                        {item.result_desc ?? item.result_code ?? "—"}
                      </TableCell>
                      <TableCell>{item.registered_by}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            <DataTablePagination
              meta={data}
              onPageChange={paging.setPage}
              onPageSizeChange={paging.setSize}
              disabled={history.isPlaceholderData}
            />
          </>
        )}
      </SectionCard>
    </div>
  );
}
