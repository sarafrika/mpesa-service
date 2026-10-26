import type { Metadata } from "next";
import { Suspense } from "react";
import { SectionBoundary } from "@/components/error-boundary";
import { OverviewDashboard } from "@/components/overview/overview-dashboard";
import { PageSkeleton } from "@/components/skeletons";
import { PaymentLookup } from "./payment-lookup";

export const metadata: Metadata = { title: "Overview" };

export default function OverviewPage() {
  return (
    <div className="flex flex-col gap-6">
      {/* The dashboard reads its filters from the URL, so it renders inside Suspense. */}
      <Suspense fallback={<PageSkeleton />}>
        <OverviewDashboard />
      </Suspense>
      <SectionBoundary title="Find a payment">
        <PaymentLookup />
      </SectionBoundary>
    </div>
  );
}
