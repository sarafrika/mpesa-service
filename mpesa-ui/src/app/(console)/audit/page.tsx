import type { Metadata } from "next";
import { Suspense } from "react";
import { AuditView } from "@/components/audit/audit-view";
import { PageHeader } from "@/components/page-header";
import { TableSkeleton } from "@/components/skeletons";

export const metadata: Metadata = { title: "Audit log" };

export default function AuditPage() {
  return (
    <>
      <PageHeader
        title="Audit log"
        description="Every change made through the console: who made it, when, and what it changed"
      />
      {/* Filters, paging and the open entry live in the URL, which needs Suspense here. */}
      <Suspense fallback={<TableSkeleton rows={10} columns={5} />}>
        <AuditView />
      </Suspense>
    </>
  );
}
