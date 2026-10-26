import type { Metadata } from "next";
import { Suspense } from "react";
import { CallbacksView } from "@/components/callbacks/callbacks-view";
import { PageHeader } from "@/components/page-header";
import { StatRowSkeleton, TableSkeleton } from "@/components/skeletons";

export const metadata: Metadata = { title: "Callback inbox" };

export default function CallbacksPage() {
  return (
    <>
      <PageHeader
        title="Callback inbox"
        description="Every request Safaricom sent to a callback URL, how it was handled, and replays of failures"
      />
      {/* Filters, paging and the open callback live in the URL, which needs Suspense here. */}
      <Suspense
        fallback={
          <>
            <StatRowSkeleton count={3} />
            <TableSkeleton rows={10} columns={6} />
          </>
        }
      >
        <CallbacksView />
      </Suspense>
    </>
  );
}
