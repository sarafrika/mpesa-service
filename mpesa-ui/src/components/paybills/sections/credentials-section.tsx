"use client";

import { KeyValueList } from "@/components/key-value-list";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { formatDateTime } from "@/lib/format";
import { UpdateCredentialsDialog } from "../update-credentials-dialog";
import type { PaybillSectionProps } from "./types";

export function CredentialsSection({ shortcode }: PaybillSectionProps) {
  const credentials = shortcode.credentials;
  const b2c = shortcode.shortcode_type === "B2C";

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
          ...(b2c
            ? []
            : [
                {
                  label: "Lipa na M-Pesa passkey",
                  value: credentials.passkey_masked ?? "Not set · STK push is off",
                  mono: Boolean(credentials.passkey_masked),
                },
              ]),
          { label: "B2C initiator", value: credentials.initiator_name ?? "Not configured" },
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
