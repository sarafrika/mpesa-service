import type { Metadata } from "next";
import { Suspense } from "react";
import { BatchScreen } from "@/components/approvals/batch-screen";
import BatchLoading from "./loading";

export const metadata: Metadata = { title: "Bulk payout" };

// The uuid, filter and page come from the URL at request time, so the screen renders inside Suspense.
export default function BatchPage() {
  return (
    <Suspense fallback={<BatchLoading />}>
      <BatchScreen />
    </Suspense>
  );
}
