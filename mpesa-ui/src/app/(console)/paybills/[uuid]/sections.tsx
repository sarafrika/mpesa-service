import type { ComponentType } from "react";
import type { ShortcodeDetail } from "@/api/generated/model";
import { C2bSection } from "@/components/paybills/sections/c2b-section";
import { CredentialsSection } from "@/components/paybills/sections/credentials-section";
import { FloatSection } from "@/components/paybills/sections/float-section";
import { OverviewSection } from "@/components/paybills/sections/overview-section";
import type { PaybillSectionProps } from "@/components/paybills/sections/types";

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

// Later slices add a tab with one entry here (validation rules, product access, features).
export const PAYBILL_SECTIONS: readonly PaybillSection[] = [
  { id: "overview", label: "Overview", Component: OverviewSection },
  {
    id: "c2b",
    label: "C2B",
    visible: (shortcode) => shortcode.shortcode_type !== "B2C",
    Component: C2bSection,
  },
  { id: "credentials", label: "Credentials", Component: CredentialsSection },
  { id: "float", label: "Float", Component: FloatSection },
];
