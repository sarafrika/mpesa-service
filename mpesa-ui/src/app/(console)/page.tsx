import type { Metadata } from "next";
import { PaymentLookup } from "./payment-lookup";

export const metadata: Metadata = { title: "Overview" };

export default function OverviewPage() {
  return (
    <>
      <div className="flex flex-col gap-1">
        <h1 className="font-semibold text-2xl tracking-tight">Overview</h1>
        <p className="text-muted-foreground text-sm">
          Collections, payouts and paybill health arrive with the admin API. Payment lookup works
          today.
        </p>
      </div>
      <PaymentLookup />
    </>
  );
}
