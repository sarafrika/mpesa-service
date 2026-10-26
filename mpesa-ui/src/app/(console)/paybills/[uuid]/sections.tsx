import type { ComponentType } from "react";
import type { ShortcodeDetail } from "@/api/generated/model";
import { ValidationRulesEditor } from "@/components/collections/validation-rules-editor";
import { C2bSection } from "@/components/paybills/sections/c2b-section";
import { CredentialsSection } from "@/components/paybills/sections/credentials-section";
import { FloatSection } from "@/components/paybills/sections/float-section";
import { OverviewSection } from "@/components/paybills/sections/overview-section";
import type { PaybillSectionProps } from "@/components/paybills/sections/types";
import { ProductAccess } from "@/components/products/product-access";
import { PaybillTransactions } from "@/components/transactions/paybill-transactions";

export type { PaybillSectionProps };

export type PaybillSection = {
  /** Tab value, kept in the URL as ?tab=<id>; unique and stable. */
  id: string;
  label: string;
  /** Leave out to show the tab on every shortcode, e.g. `(s) => s.shortcode_type !== "B2C"`. */
  visible?: (shortcode: ShortcodeDetail) => boolean;
  /** Rendered inside its own SectionBoundary, so a failing tab never breaks the page. */
  Component: ComponentType<PaybillSectionProps>;
};

function ValidationRulesSection({ shortcode }: PaybillSectionProps) {
  return <ValidationRulesEditor shortcodeUuid={shortcode.uuid} />;
}

function TransactionsSection({ shortcode }: PaybillSectionProps) {
  return <PaybillTransactions shortcodeUuid={shortcode.uuid} />;
}

function ProductAccessSection({ shortcode }: PaybillSectionProps) {
  return <ProductAccess shortcodeUuid={shortcode.uuid} />;
}

// One entry per tab; a later slice adds its tab here (features).
export const PAYBILL_SECTIONS: readonly PaybillSection[] = [
  { id: "overview", label: "Overview", Component: OverviewSection },
  {
    id: "c2b",
    label: "C2B",
    visible: (shortcode) => shortcode.shortcode_type !== "B2C",
    Component: C2bSection,
  },
  {
    id: "validation",
    label: "Validation rules",
    visible: (shortcode) => shortcode.shortcode_type !== "B2C",
    Component: ValidationRulesSection,
  },
  { id: "products", label: "Product access", Component: ProductAccessSection },
  { id: "credentials", label: "Credentials", Component: CredentialsSection },
  { id: "float", label: "Float", Component: FloatSection },
  { id: "transactions", label: "Transactions", Component: TransactionsSection },
];
