"use client";

import { useListShortcodeFeatures } from "@/api/generated/endpoints/admin-features/admin-features";
import { SectionBoundary } from "@/components/error-boundary";
import { okData } from "@/components/paybills/api";
import { can } from "@/components/paybills/capabilities";
import type { PaybillSectionProps } from "@/components/paybills/sections/types";
import { RoleGate } from "@/components/role-gate";
import { B2bCheckoutDialog } from "./b2b-checkout-dialog";
import { B2bCheckouts } from "./b2b-checkouts-table";
import type { Feature } from "./feature-catalog";
import { FeatureSwitches } from "./feature-switches";
import { PochiPayoutDialog } from "./pochi-payout-dialog";
import { PochiPayouts, TaxRemittances } from "./product-payouts-table";
import { TaxRemittanceDialog } from "./tax-remittance-dialog";

/** The Features tab: the switches, then each built product's action and records. */
export function FeaturesSection({ shortcode }: PaybillSectionProps) {
  const features = useListShortcodeFeatures(shortcode.uuid);
  const rows = okData(features.data);
  const on = (feature: Feature) =>
    rows?.some((row) => row.feature === feature && row.enabled) ?? false;
  const active = shortcode.is_active;

  return (
    <div className="grid gap-4">
      <SectionBoundary title="Features">
        <FeatureSwitches shortcodeUuid={shortcode.uuid} shortcodeType={shortcode.shortcode_type} />
      </SectionBoundary>
      <SectionBoundary title="Tax remittances">
        <TaxRemittances
          shortcodeUuid={shortcode.uuid}
          action={
            <RoleGate needs="mpesa-operator">
              <TaxRemittanceDialog
                shortcodeUuid={shortcode.uuid}
                disabled={!on("TAX_REMITTANCE") || !active}
              />
            </RoleGate>
          }
        />
      </SectionBoundary>
      {can(shortcode, "POCHI") && (
        <SectionBoundary title="Pochi payments">
          <PochiPayouts
            shortcodeUuid={shortcode.uuid}
            action={
              <RoleGate needs="mpesa-operator">
                <PochiPayoutDialog
                  shortcodeUuid={shortcode.uuid}
                  disabled={!on("BUSINESS_TO_POCHI") || !active}
                />
              </RoleGate>
            }
          />
        </SectionBoundary>
      )}
      {can(shortcode, "B2B_EXPRESS_CHECKOUT") && (
        <SectionBoundary title="B2B Express Checkout">
          <B2bCheckouts
            shortcodeUuid={shortcode.uuid}
            action={
              <RoleGate needs="mpesa-operator">
                <B2bCheckoutDialog
                  shortcodeUuid={shortcode.uuid}
                  businessName={shortcode.business_name}
                  disabled={!on("B2B_EXPRESS_CHECKOUT") || !active}
                />
              </RoleGate>
            }
          />
        </SectionBoundary>
      )}
    </div>
  );
}
