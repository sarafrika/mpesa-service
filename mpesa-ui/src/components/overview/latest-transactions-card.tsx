"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import { ArrowLeftRight } from "lucide-react";
import Link from "next/link";
import type { ApiError, LatestTransactionDto } from "@/api/generated/model";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { Money } from "@/components/money";
import { SectionCard } from "@/components/section-card";
import { TableSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";

const TYPE_LABEL: Record<string, string> = {
  C2B: "C2B deposit",
  STK_PUSH: "STK push",
  B2C: "B2C payout",
  B2B: "B2B transfer",
};

const time = new Intl.DateTimeFormat("en-KE", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
  timeZone: "Africa/Nairobi",
});
const day = new Intl.DateTimeFormat("en-KE", {
  day: "numeric",
  month: "short",
  timeZone: "Africa/Nairobi",
});

function when(iso: string, now: number) {
  const date = new Date(iso);
  const sameDay = day.format(date) === day.format(new Date(now));
  return sameDay ? time.format(date) : `${day.format(date)}, ${time.format(date)}`;
}

type LatestTransactionsCardProps = {
  query: UseQueryResult<LatestTransactionDto[], ApiError>;
  now: number | null;
};

export function LatestTransactionsCard({ query, now }: LatestTransactionsCardProps) {
  const rows = query.data;
  return (
    <SectionCard
      title="Latest transactions"
      description="Nairobi time"
      className="min-w-0"
      actions={
        <Button asChild variant="link" size="sm" className="px-0">
          <Link href="/transactions">Open explorer →</Link>
        </Button>
      }
    >
      <StaleNotice query={query} />
      {!rows ? (
        query.isError ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : (
          <TableSkeleton rows={6} columns={6} />
        )
      ) : rows.length === 0 ? (
        <EmptyState
          icon={ArrowLeftRight}
          title="No transactions yet"
          description="STK pushes, C2B deposits and B2C payouts on your paybills appear here as they happen."
        />
      ) : (
        <div
          className={cn(
            "overflow-x-auto rounded-lg border",
            query.isPlaceholderData && query.isFetching && "opacity-60",
          )}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead scope="col">Time</TableHead>
                <TableHead scope="col">Type</TableHead>
                <TableHead scope="col">Paybill</TableHead>
                <TableHead scope="col">Reference</TableHead>
                <TableHead scope="col" className="text-right">
                  Amount (KES)
                </TableHead>
                <TableHead scope="col">Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={`${row.direction}-${row.uuid}`}>
                  <TableCell className="text-muted-foreground tabular-nums">
                    {when(row.created_at, now ?? Date.parse(row.created_at))}
                  </TableCell>
                  <TableCell>{TYPE_LABEL[row.payment_type] ?? row.payment_type}</TableCell>
                  <TableCell title={row.business_name}>{row.shortcode}</TableCell>
                  <TableCell className="max-w-40 truncate font-mono text-xs" title={row.reference}>
                    {row.reference ?? "—"}
                  </TableCell>
                  <TableCell className="text-right font-medium">
                    <Money
                      amount={row.amount}
                      direction={row.direction === "IN" ? "in" : "out"}
                      hideCode
                    />
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={row.status} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </SectionCard>
  );
}
