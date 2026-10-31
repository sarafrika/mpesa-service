"use client";

import { Info } from "lucide-react";
import { useGetValidationRules } from "@/api/generated/endpoints/admin-c2b-validation/admin-c2b-validation";

/** Rules only run once Safaricom switches on External Validation for the shortcode (C2B FAQs, D/FAQ). */
export function ExternalValidationNotice({ shortcodeUuid }: { shortcodeUuid: string }) {
  const query = useGetValidationRules(shortcodeUuid);
  const rules = query.data?.status === 200 ? query.data.data : undefined;
  if (!rules?.configured || !rules.active) return null;
  return (
    <div className="flex items-start gap-2.5 rounded-lg bg-info-soft px-3 py-2.5 text-info text-sm">
      <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
      <span>
        Safaricom must switch on External Validation before these rules see any payment. Email{" "}
        <a className="font-medium underline" href="mailto:apisupport@safaricom.co.ke">
          apisupport@safaricom.co.ke
        </a>{" "}
        with the shortcode and ask for it; it takes up to 24 working hours.
      </span>
    </div>
  );
}
