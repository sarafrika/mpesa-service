import type { Metadata } from "next";
import { SectionBoundary } from "@/components/error-boundary";
import { AddPaybillWizard } from "@/components/paybills/wizard/add-paybill-wizard";

export const metadata: Metadata = { title: "Add paybill" };

export default function AddPaybillPage() {
  return (
    <SectionBoundary title="Add a paybill or till">
      <AddPaybillWizard />
    </SectionBoundary>
  );
}
