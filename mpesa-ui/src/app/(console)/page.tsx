import type { Metadata } from "next";
import { SectionBoundary } from "@/components/error-boundary";
import { PageHeader } from "@/components/page-header";
import { PaymentLookup } from "./payment-lookup";

export const metadata: Metadata = { title: "Overview" };

export default function OverviewPage() {
  return (
    <>
      <PageHeader
        title="Overview"
        description="Collections, payouts and paybill health arrive with the admin API. Payment lookup works today."
      />
      <SectionBoundary title="Find a payment">
        <PaymentLookup />
      </SectionBoundary>
    </>
  );
}
