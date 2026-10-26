"use client";

import { TriangleAlert } from "lucide-react";
import { KeyValueList } from "@/components/key-value-list";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { formatDateTime } from "@/lib/format";
import { RotateCredentialsDialog } from "../rotate-credentials-dialog";
import type { PaybillSectionProps } from "./types";

export function CredentialsSection({ shortcode }: PaybillSectionProps) {
  const credentials = shortcode.credentials;
  const b2c = shortcode.shortcode_type === "B2C";

  return (
    <SectionCard
      title="Daraja credentials"
      description="Write-only. Stored encrypted; only the last four characters are ever shown. Rotation needs mpesa-admin and is audited."
      actions={
        <RoleGate needs="mpesa-admin">
          <RotateCredentialsDialog shortcode={shortcode} />
        </RoleGate>
      }
    >
      <KeyValueList
        items={[
          { label: "Consumer key", value: credentials.consumer_key_masked, mono: true },
          {
            label: "Consumer secret",
            value: `${credentials.consumer_secret_masked}${credentials.rotated_at ? ` · rotated ${formatDateTime(credentials.rotated_at)}` : ""}`,
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
        ]}
      />
      {credentials.rotation_due && (
        <div
          role="status"
          className="flex items-center gap-2.5 rounded-lg bg-warning-soft px-3 py-2.5 font-medium text-sm text-warning"
        >
          <TriangleAlert className="size-4 shrink-0" aria-hidden />
          Credentials are {credentials.age_days ?? `over ${credentials.rotation_days}`} days old.
          Sarafrika policy rotates at {credentials.rotation_days} days.
        </div>
      )}
    </SectionCard>
  );
}
