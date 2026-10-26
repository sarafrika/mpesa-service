import type { Metadata } from "next";
import { Suspense } from "react";
import { PaybillsScreen } from "@/components/paybills/paybills-screen";
import PaybillsLoading from "./loading";

export const metadata: Metadata = { title: "Paybills & tills" };

// The screen reads its filters and page from the URL, so it renders inside Suspense.
export default function PaybillsPage() {
  return (
    <Suspense fallback={<PaybillsLoading />}>
      <PaybillsScreen />
    </Suspense>
  );
}
