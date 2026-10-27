"use client";

import { KeyValueList } from "@/components/key-value-list";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { StatusBadge } from "@/components/status-badge";
import { formatDateTime } from "@/lib/format";
import { can, hasInitiator, paysOut } from "../capabilities";
import { initiatorPurpose } from "../shortcode-types";
import { UpdateCredentialsDialog } from "../update-credentials-dialog";
import type { PaybillSectionProps } from "./types";

export function CredentialsSection({ shortcode }: PaybillSectionProps) {
  const credentials = shortcode.credentials;
  const stk = can(shortcode, "STK_PUSH");
  const required = paysOut(shortcode);
  const till = shortcode.shortcode_type === "TILL";

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
          ...(stk
            ? [
                {
                  label: "Lipa na M-Pesa passkey",
                  value: credentials.passkey_masked
                    ? `${credentials.passkey_masked}${till && shortcode.store_number ? ` · for store ${shortcode.store_number}` : ""}`
                    : "Not set · STK push is off",
                  mono: Boolean(credentials.passkey_masked),
                },
              ]
            : []),
          {
            label: "API initiator",
            value: (
              <span className="flex flex-col gap-1">
                <span className="flex flex-wrap items-center gap-2">
                  {credentials.initiator_name ?? "Not configured"}
                  {required && !hasInitiator(credentials) && (
                    <StatusBadge status="Needed to go live" tone="warning" />
                  )}
                </span>
                <span className="text-muted-foreground text-xs">{initiatorPurpose(required)}</span>
              </span>
            ),
          },
          {
            label: "Initiator password",
            value: credentials.initiator_password_set ? "Set" : "Not set",
          },
          {
            label: "Security credential",
            value: credentials.security_credential_set
              ? "Generated on the Daraja portal"
              : credentials.initiator_password_set
                ? "Encrypted from the initiator password with Safaricom's certificate"
                : "Not set",
          },
          {
            label: "Last updated",
            value: credentials.updated_at ? formatDateTime(credentials.updated_at) : "Unknown",
          },
        ]}
      />
    </SectionCard>
  );
}
