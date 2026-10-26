"use client";

import { Check, Copy, TriangleAlert } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";

const keepOpen = (event: Event) => event.preventDefault();

/** Spread on the DialogContent showing a secret: only its "I've stored it" button closes it. */
export const secretDialogGuards = {
  showCloseButton: false,
  onEscapeKeyDown: keepOpen,
  onInteractOutside: keepOpen,
} as const;

/** Shows a webhook signing secret the one time the API returns it, with a copy button. */
export function SecretReveal({ secret }: { secret: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("Couldn't copy", { description: "Select the secret and copy it by hand." });
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <div
        role="alert"
        className="flex gap-2 rounded-lg border border-warning/30 bg-warning-soft p-3 text-sm text-warning"
      >
        <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
        <p>
          Copy this signing secret into the product&apos;s secret store now. It is shown only once;
          if it is lost, rotate it.
        </p>
      </div>
      <div className="flex items-center gap-2 rounded-lg border bg-muted/50 p-2">
        <code
          className="min-w-0 flex-1 break-all font-mono text-[13px]"
          data-testid="webhook-secret"
        >
          {secret}
        </code>
        <Button type="button" variant="outline" size="sm" onClick={copy}>
          {copied ? <Check /> : <Copy />}
          {copied ? "Copied" : "Copy"}
        </Button>
      </div>
      <p className="text-muted-foreground text-xs">
        Verify each delivery by recomputing HMAC-SHA256 over <code>t.body</code> and comparing it
        with <code>v1</code> in the <code>X-Sarafrika-Signature</code> header.
      </p>
    </div>
  );
}
