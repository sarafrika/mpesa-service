"use client";

import type { ShortcodeDetail } from "@/api/generated/model";
import { ValidationRulesEditor } from "@/components/collections/validation-rules-editor";

export type ValidationRulesSlotProps = {
  /** The draft saved at the end of step 2; rules attach to its uuid. */
  shortcode: ShortcodeDetail;
};

/** Step 3 of the wizard, paybills and tills only: the compact C2B validation-rules editor. */
export function ValidationRulesSlot({ shortcode }: ValidationRulesSlotProps) {
  return (
    <div data-slot="validation-rules" className="flex flex-col gap-2">
      <h3 className="font-semibold text-sm">Validation rules</h3>
      <ValidationRulesEditor shortcodeUuid={shortcode.uuid} compact />
    </div>
  );
}
