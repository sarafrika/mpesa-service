import type { Metadata } from "next";
import { Suspense } from "react";
import { PageHeader } from "@/components/page-header";
import { TableSkeleton } from "@/components/skeletons";
import { TransactionsView } from "@/components/transactions/transactions-view";

export const metadata: Metadata = { title: "Transactions" };

export default function TransactionsPage() {
  return (
    <>
      <PageHeader
        title="Transactions"
        description="Every deposit, STK request and payout across all paybills."
      />
      {/* Filters, paging and the open transaction live in the URL, which needs Suspense here. */}
      <Suspense fallback={<TableSkeleton rows={10} columns={7} />}>
        <TransactionsView />
      </Suspense>
    </>
  );
}
