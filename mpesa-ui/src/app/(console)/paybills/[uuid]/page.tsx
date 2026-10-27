import type { Metadata } from "next";
import { Suspense } from "react";
import PaybillLoading from "./loading";
import { PaybillDetailClient } from "./paybill-detail-client";

export const metadata: Metadata = { title: "Shortcode" };

// The uuid and the open tab come from the URL at request time, so the page renders inside Suspense.
export default function PaybillPage() {
  return (
    <Suspense fallback={<PaybillLoading />}>
      <PaybillDetailClient />
    </Suspense>
  );
}
