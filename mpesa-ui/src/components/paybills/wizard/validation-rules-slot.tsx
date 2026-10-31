"use client";

import type { ShortcodeDetail } from "@/api/generated/model";
import { ExternalValidationNotice } from "@/components/collections/external-validation-notice";
import { ValidationRulesEditor } from "@/components/collections/validation-rules-editor";
import { takesAccountNumbers } from "../capabilities";

export type ValidationRulesSlotProps = {
  /** The draft saved at the end of the Credentials step; rules attach to its uuid. */
  shortcode: ShortcodeDetail;
};

/** The wizard's Callbacks & rules step, when it receives C2B: the compact validation-rules editor. */
export function ValidationRulesSlot({ shortcode }: ValidationRulesSlotProps) {
  return (
    <div data-slot="validation-rules" className="flex flex-col gap-2">
      <h3 className="font-semibold text-sm">Validation rules</h3>
      <ExternalValidationNotice shortcodeUuid={shortcode.uuid} />
      <ValidationRulesEditor
        shortcodeUuid={shortcode.uuid}
        accountNumbers={takesAccountNumbers(shortcode)}
        compact
      />
    </div>
  );
}
