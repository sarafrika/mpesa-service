"use client";

import { TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { useUpdateShortcodeOperations } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import { SectionCard } from "@/components/section-card";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { useHasRole } from "@/hooks/use-has-role";
import { isApiError } from "@/lib/api-errors";
import { toastApiError, useInvalidateShortcodes } from "../api";
import {
  darajaProducts,
  missingCredential,
  needsText,
  OPERATION_INFO,
  type Operation,
  ordered,
  toggled,
} from "../operations";
import { typeNoun } from "../shortcode-types";
import { UpdateCredentialsDialog } from "../update-credentials-dialog";
import type { PaybillSectionProps } from "./types";

const MISSING_COPY = {
  passkey: "Needs the passkey",
  initiator: "Needs the API initiator",
} as const;

/** Turns operations on and off (mpesa-admin, like credentials); each decides which credentials it needs. */
export function OperationsSection({ shortcode }: PaybillSectionProps) {
  const isAdmin = useHasRole("mpesa-admin");
  const invalidate = useInvalidateShortcodes();
  const [refusal, setRefusal] = useState<string>();
  const [waiting, setWaiting] = useState<Operation>();
  const update = useUpdateShortcodeOperations({ mutation: { meta: { errorToast: false } } });
  const enabled = ordered(shortcode.operations);
  const available = ordered(shortcode.available_operations);
  const active = shortcode.status === "ACTIVE";
  const products = darajaProducts(enabled);
  const noun = typeNoun(shortcode.shortcode_type);

  async function save(next: Operation[], operation: Operation, on: boolean) {
    setRefusal(undefined);
    try {
      await update.mutateAsync({ uuid: shortcode.uuid, data: { operations: next } });
      toast.success(`${OPERATION_INFO[operation].label} turned ${on ? "on" : "off"}`);
      await invalidate();
    } catch (error) {
      // 409: an active shortcode lacks the credential; the service's sentence names it.
      if (isApiError(error) && error.status === 409) setRefusal(error.serverMessage);
      else toastApiError(error, { requiredRole: "mpesa-admin" });
    }
  }

  function flip(operation: Operation, on: boolean) {
    // Live shortcodes refuse an operation whose credential is missing, so ask for it first.
    if (on && active && missingCredential(operation, shortcode.credentials)) {
      setWaiting(operation);
      return;
    }
    void save(toggled(enabled, operation, on), operation, on);
  }

  return (
    <SectionCard
      title="Operations"
      description={`What this ${noun} is set up to do. Each one decides which credentials it needs; anything off is refused.`}
    >
      {refusal && (
        <div
          role="alert"
          className="flex flex-wrap items-center gap-3 rounded-lg border border-warning/30 bg-warning-soft p-3.5 text-sm text-warning"
        >
          <TriangleAlert className="size-4 shrink-0" aria-hidden />
          <span className="min-w-0 flex-[1_1_16rem]">{refusal}</span>
          <Button variant="outline" size="sm" asChild>
            <Link href={`/paybills/${shortcode.uuid}?tab=credentials`}>Open Credentials</Link>
          </Button>
        </div>
      )}
      <ul className="divide-y rounded-lg border">
        {available.map((operation) => {
          const info = OPERATION_INFO[operation];
          const on = enabled.includes(operation);
          const missing = on ? missingCredential(operation, shortcode.credentials) : undefined;
          const id = `operation-${operation}`;
          return (
            <li key={operation} className="flex items-start justify-between gap-4 p-4">
              <div className="flex min-w-0 flex-col gap-1">
                <label htmlFor={id} className="font-medium">
                  {info.label}
                </label>
                <p className="text-muted-foreground text-sm">{info.does}</p>
                <p className="text-muted-foreground text-xs">{needsText(operation)}</p>
                {missing && (
                  <span>
                    <StatusBadge status={MISSING_COPY[missing]} tone="warning" />
                  </span>
                )}
              </div>
              <Switch
                id={id}
                checked={on}
                disabled={!isAdmin || update.isPending}
                aria-label={`${info.label} ${on ? "on" : "off"}`}
                onCheckedChange={(checked) => flip(operation, checked)}
              />
            </li>
          );
        })}
      </ul>
      {products.length > 0 && (
        <p className="text-muted-foreground text-sm">
          On the Daraja portal, the app for this {noun} needs{" "}
          {products.length === 1 ? "the product" : "these products"} under My Apps:{" "}
          <span className="font-medium text-foreground">{products.join(", ")}</span>.
        </p>
      )}
      {!isAdmin && (
        <p className="text-muted-foreground text-sm">
          Only an admin can turn operations on or off.
        </p>
      )}
      {waiting && (
        <UpdateCredentialsDialog
          shortcode={shortcode}
          open
          onOpenChange={(open) => !open && setWaiting(undefined)}
          forOperations={[waiting]}
          reason={`${OPERATION_INFO[waiting].label} needs ${
            missingCredential(waiting, shortcode.credentials) === "passkey"
              ? "the Lipa na M-Pesa passkey"
              : "the API initiator"
          }. Save it here and it is turned on.`}
          onSaved={() => save(toggled(enabled, waiting, true), waiting, true)}
        />
      )}
    </SectionCard>
  );
}
