"use client";

import { SectionBoundary } from "@/components/error-boundary";
import { B2bDestinations } from "@/components/treasury/b2b-destinations";
import { B2bTransfers } from "@/components/treasury/b2b-transfers";
import { FloatBalances } from "@/components/treasury/float-balances";
import type { PaybillSectionProps } from "./types";

/** Balances and history, the low-float line, B2B destinations and the sweeps sent from this shortcode. */
export function FloatSection({ shortcode }: PaybillSectionProps) {
  return (
    <div className="grid gap-4">
      <SectionBoundary title="Float">
        <FloatBalances shortcode={shortcode} />
      </SectionBoundary>
      <SectionBoundary title="B2B destinations">
        <B2bDestinations shortcodeUuid={shortcode.uuid} />
      </SectionBoundary>
      <SectionBoundary title="B2B transfers">
        <B2bTransfers shortcodeUuid={shortcode.uuid} />
      </SectionBoundary>
    </div>
  );
}
