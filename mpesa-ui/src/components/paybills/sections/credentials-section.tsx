"use client";

import { useState } from "react";
import { KeyValueList } from "@/components/key-value-list";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { StatusBadge } from "@/components/status-badge";
import { formatDateTime } from "@/lib/format";
import { hasInitiator } from "../capabilities";
import { credentialFields, initiatorExpiry, initiatorRoles, initiatorUse } from "../operations";
import { UpdateCredentialsDialog } from "../update-credentials-dialog";
import type { PaybillSectionProps } from "./types";

export function CredentialsSection({ shortcode }: PaybillSectionProps) {
  const [now] = useState(() => Date.now());
  const credentials = shortcode.credentials;
  const needs = credentialFields(shortcode.operations);
  const till = shortcode.shortcode_type === "TILL";
  const initiatorStored =
    Boolean(credentials.initiator_name) ||
    credentials.initiator_password_set ||
    credentials.security_credential_set;
  const roles = initiatorRoles(shortcode.operations);

  return (
    <SectionCard
      title="Daraja credentials"
      description="Write-only. Stored encrypted; only the last four characters are ever shown. Change them at Safaricom first, then update them here (mpesa-admin, audited)."
      actions={
        <RoleGate needs="mpesa-admin">
          <UpdateCredentialsDialog shortcode={shortcode} />
        </RoleGate>
      }
    >
      <KeyValueList
        items={[
          { label: "Consumer key", value: credentials.consumer_key_masked, mono: true },
          {
            label: "Consumer secret",
            value: credentials.consumer_secret_masked,
            mono: true,
          },
          ...(needs.passkey || credentials.passkey_masked
            ? [
                {
                  label: "Lipa na M-Pesa passkey",
                  value: credentials.passkey_masked ? (
                    `${credentials.passkey_masked}${till && shortcode.store_number ? ` · for store ${shortcode.store_number}` : ""}`
                  ) : (
                    <span className="flex flex-wrap items-center gap-2">
                      Not set
                      <StatusBadge status="STK push needs it" tone="warning" />
                    </span>
                  ),
                  mono: Boolean(credentials.passkey_masked),
                },
              ]
            : []),
          ...(needs.initiator || initiatorStored
            ? [
                {
                  label: "API initiator",
                  value: (
                    <span className="flex flex-col gap-1">
                      <span className="flex flex-wrap items-center gap-2">
                        {credentials.initiator_name ?? "Not configured"}
                        {needs.initiator && !hasInitiator(credentials) && (
                          <StatusBadge status="Needed" tone="warning" />
                        )}
                      </span>
                      <span className="font-normal text-muted-foreground text-xs">
                        {initiatorUse(shortcode.operations)}
                        {roles.length > 0 && ` Org-portal roles: ${roles.join(", ")}.`}
                      </span>
                    </span>
                  ),
                },
                {
                  label: "Initiator password",
                  value: (
                    <InitiatorPasswordState
                      set={
                        credentials.initiator_password_set || credentials.security_credential_set
                      }
                      label={credentials.initiator_password_set ? "Set" : "Not set"}
                      expiresAt={shortcode.initiator_credential_expires_at}
                      now={now}
                    />
                  ),
                },
                {
                  label: "Security credential",
                  value: credentials.security_credential_set
                    ? "Generated on the Daraja portal"
                    : credentials.initiator_password_set
                      ? "Encrypted from the initiator password with Safaricom's certificate"
                      : "Not set",
                },
              ]
            : [
                {
                  label: "API initiator",
                  value: (
                    <span className="font-normal text-muted-foreground">
                      Not needed: nothing turned on under Operations uses it.
                    </span>
                  ),
                },
              ]),
          {
            label: "Last updated",
            value: credentials.updated_at ? formatDateTime(credentials.updated_at) : "Unknown",
          },
        ]}
      />
    </SectionCard>
  );
}

/** "Set · Expires in 12 days": Safaricom initiator passwords last 90 days. */
function InitiatorPasswordState({
  set,
  label,
  expiresAt,
  now,
}: {
  set: boolean;
  label: string;
  expiresAt?: string | null;
  now: number;
}) {
  const expiry = set ? initiatorExpiry(expiresAt, now) : undefined;
  if (!expiry) return label;
  return (
    <span className="flex flex-wrap items-center gap-2">
      {label}
      {expiry.tone === "neutral" ? (
        <span className="font-normal text-muted-foreground">· {expiry.label}</span>
      ) : (
        <StatusBadge status={expiry.label} tone={expiry.tone} />
      )}
      {expiry.tone !== "neutral" && (
        <span className="basis-full font-normal text-muted-foreground text-xs">
          A Business Manager sets a new password on the M-PESA org portal; then save it here.
        </span>
      )}
    </span>
  );
}
