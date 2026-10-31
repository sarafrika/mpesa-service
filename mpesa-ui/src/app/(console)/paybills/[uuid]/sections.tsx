import type { ComponentType } from "react";
import { ExternalValidationNotice } from "@/components/collections/external-validation-notice";
import { ValidationRulesEditor } from "@/components/collections/validation-rules-editor";
import { FeaturesSection } from "@/components/daraja-products/features-section";
import {
  grantableOperations,
  PAYBILL_TABS,
  type PaybillTabId,
  type TabRule,
  takesAccountNumbers,
} from "@/components/paybills/capabilities";
import { C2bSection } from "@/components/paybills/sections/c2b-section";
import { CredentialsSection } from "@/components/paybills/sections/credentials-section";
import { FloatSection } from "@/components/paybills/sections/float-section";
import { OperationsSection } from "@/components/paybills/sections/operations-section";
import { OverviewSection } from "@/components/paybills/sections/overview-section";
import type { PaybillSectionProps } from "@/components/paybills/sections/types";
import { ProductAccess } from "@/components/products/product-access";
import { PaybillTransactions } from "@/components/transactions/paybill-transactions";

export type { PaybillSectionProps };

export type PaybillSection = TabRule & {
  /** Tab value, kept in the URL as ?tab=<id>; unique and stable. */
  id: string;
  label: string;
  /** Rendered inside its own SectionBoundary, so a failing tab never breaks the page. */
  Component: ComponentType<PaybillSectionProps>;
};

function ValidationRulesSection({ shortcode }: PaybillSectionProps) {
  return (
    <div className="grid gap-4">
      <ExternalValidationNotice shortcodeUuid={shortcode.uuid} />
      <ValidationRulesEditor
        shortcodeUuid={shortcode.uuid}
        accountNumbers={takesAccountNumbers(shortcode)}
      />
    </div>
  );
}

function TransactionsSection({ shortcode }: PaybillSectionProps) {
  return <PaybillTransactions shortcodeUuid={shortcode.uuid} />;
}

function ProductAccessSection({ shortcode }: PaybillSectionProps) {
  return (
    <ProductAccess shortcodeUuid={shortcode.uuid} operations={grantableOperations(shortcode)} />
  );
}

const COMPONENTS: Record<PaybillTabId, ComponentType<PaybillSectionProps>> = {
  overview: OverviewSection,
  operations: OperationsSection,
  c2b: C2bSection,
  validation: ValidationRulesSection,
  products: ProductAccessSection,
  credentials: CredentialsSection,
  float: FloatSection,
  transactions: TransactionsSection,
  features: FeaturesSection,
};

// Which tabs a shortcode gets comes from its capabilities (PAYBILL_TABS[].needs), never from its type.
export const PAYBILL_SECTIONS: readonly PaybillSection[] = PAYBILL_TABS.map((tab) => ({
  ...tab,
  Component: COMPONENTS[tab.id],
}));
