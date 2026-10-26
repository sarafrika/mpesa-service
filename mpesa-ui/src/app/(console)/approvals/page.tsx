import type { Metadata } from "next";
import { Suspense } from "react";
import { ApprovalsScreen } from "@/components/approvals/approvals-screen";
import ApprovalsLoading from "./loading";

export const metadata: Metadata = { title: "Approvals" };

// The tab, page and open request live in the URL, so the screen renders inside Suspense.
export default function ApprovalsPage() {
  return (
    <Suspense fallback={<ApprovalsLoading />}>
      <ApprovalsScreen />
    </Suspense>
  );
}
