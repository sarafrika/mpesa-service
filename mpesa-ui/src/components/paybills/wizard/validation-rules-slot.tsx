"use client";

import { ListChecks } from "lucide-react";
import type { ShortcodeDetail } from "@/api/generated/model";

export type ValidationRulesSlotProps = {
  /** The draft saved at the end of step 2; rules attach to its uuid. */
  shortcode: ShortcodeDetail;
};

// SLOT for the collections slice: replace this body with the C2B validation-rules editor.
// The wizard renders it in step 3 for paybills and tills (never B2C), after the draft exists.
export function ValidationRulesSlot({ shortcode }: ValidationRulesSlotProps) {
  return (
    <div
      data-slot="validation-rules"
      className="flex items-start gap-3 rounded-lg border border-dashed p-4 text-sm"
    >
      <ListChecks className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
      <div className="flex flex-col gap-1">
        <span className="font-medium">Validation rules</span>
        <span className="text-muted-foreground">
          Account-number patterns, limits and product lookups for {shortcode.business_name} are set
          here once configurable C2B validation ships. Until then the service's built-in validation
          applies.
        </span>
      </div>
    </div>
  );
}
