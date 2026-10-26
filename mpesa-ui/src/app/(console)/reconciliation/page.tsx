import type { Metadata } from "next";
import { Suspense } from "react";
import { ReconciliationScreen } from "@/components/reconciliation/reconciliation-screen";
import ReconciliationLoading from "./loading";

export const metadata: Metadata = { title: "Reconciliation" };

export default function ReconciliationPage() {
  return (
    // The paybill, run, filters and paging live in the URL, which needs Suspense here.
    <Suspense fallback={<ReconciliationLoading />}>
      <ReconciliationScreen />
    </Suspense>
  );
}
