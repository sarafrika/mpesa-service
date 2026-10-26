import type { Metadata } from "next";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { RoleGate } from "@/components/role-gate";
import { TableSkeleton } from "@/components/skeletons";
import { TransactionsToday } from "@/components/transactions/transactions-today";
import { TransactionsExport, TransactionsView } from "@/components/transactions/transactions-view";

export const metadata: Metadata = { title: "Transactions" };

export default function TransactionsPage() {
  return (
    <>
      <PageHeader
        title="Transactions"
        actions={
          <Suspense fallback={null}>
            <RoleGate needs="mpesa-finance">
              <TransactionsExport />
            </RoleGate>
          </Suspense>
        }
        description={
          <>
            Every deposit, STK request and payout across all paybills
            <TransactionsToday />
          </>
        }
      />
      {/* Filters, paging and the open transaction live in the URL, which needs Suspense here. */}
      <Suspense fallback={<TableSkeleton rows={10} columns={7} />}>
        <TransactionsView />
      </Suspense>
    </>
  );
}
