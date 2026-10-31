"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { KeyRound } from "lucide-react";
import { type ReactNode, useEffect, useState } from "react";
import { type Path, useForm } from "react-hook-form";
import { toast } from "sonner";
import type { z } from "zod";
import { useUpdateShortcodeCredentials } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import type { ShortcodeDetail } from "@/api/generated/model";
import { UpdateShortcodeCredentialsBody } from "@/api/generated/zod/admin-shortcodes/admin-shortcodes.zod";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { applyFieldErrors } from "@/lib/api-errors";
import { okData, toastApiError, useInvalidateShortcodes } from "./api";
import { FormRootError, TextField } from "./form-fields";
import {
  credentialFields,
  initiatorRoles,
  initiatorUse,
  type Operation,
  ordered,
} from "./operations";
import { CREDENTIAL_HINTS } from "./shortcode-types";

const FIELDS = [
  "consumer_key",
  "consumer_secret",
  "passkey",
  "initiator_name",
  "initiator_password",
  "security_credential",
] as const;

const schema = UpdateShortcodeCredentialsBody.refine(
  (values) => FIELDS.some((field) => (values[field] ?? "").trim() !== ""),
  { path: ["root"], message: "Enter at least one new credential" },
);
type Values = z.infer<typeof schema>;
const EMPTY: Values = Object.fromEntries(FIELDS.map((field) => [field, ""])) as Values;

type UpdateCredentialsDialogProps = {
  shortcode: ShortcodeDetail;
  /** Pass with `onOpenChange` to open it from elsewhere, e.g. when an operation needs a credential. */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  /** Also shows the fields these operations need, before they are turned on. */
  forOperations?: readonly Operation[];
  /** Why it opened, shown above the fields. */
  reason?: ReactNode;
  onSaved?: (saved: ShortcodeDetail | undefined) => unknown;
};

/** mpesa-admin only. Daraja has no API to change credentials, so this stores values already changed at
 * Safaricom. They are write-only: they leave the browser once and are never shown again. */
export function UpdateCredentialsDialog({
  shortcode,
  open: controlledOpen,
  onOpenChange,
  forOperations = [],
  reason,
  onSaved,
}: UpdateCredentialsDialogProps) {
  const [ownOpen, setOwnOpen] = useState(false);
  const open = controlledOpen ?? ownOpen;
  const invalidate = useInvalidateShortcodes();
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: EMPTY });
  const update = useUpdateShortcodeCredentials({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;
  const credentials = shortcode.credentials;
  const operations = ordered([...shortcode.operations, ...forOperations]);
  const shown = credentialFields(operations);
  // A credential stored earlier stays editable even when nothing turned on uses it now.
  const passkey = shown.passkey || Boolean(credentials.passkey_masked);
  const initiator =
    shown.initiator ||
    Boolean(credentials.initiator_name) ||
    credentials.initiator_password_set ||
    credentials.security_credential_set;
  const roles = initiatorRoles(operations);
  const till = shortcode.shortcode_type === "TILL";
  const production = shortcode.environment === "PRODUCTION";

  // Opening, from the trigger or from a parent, always starts from empty fields.
  useEffect(() => {
    if (open) form.reset(EMPTY);
  }, [open, form]);

  function setOpen(next: boolean) {
    setOwnOpen(next);
    onOpenChange?.(next);
  }

  async function submit(values: Values) {
    const data = Object.fromEntries(
      FIELDS.map((field) => [field, values[field]?.trim() || undefined]),
    ) as Values;
    try {
      const saved = okData(await update.mutateAsync({ uuid: shortcode.uuid, data }));
      if (saved?.health.token_ok) {
        toast.success("Credentials updated", { description: "Daraja issued a token for them." });
      } else {
        toast.warning("Credentials saved, but the connection check failed", {
          description:
            saved?.health.token_error ?? "Daraja did not issue a token for these credentials.",
        });
      }
      await invalidate();
      setOpen(false);
      await onSaved?.(saved);
    } catch (error) {
      // "credentials" (nothing entered) belongs to the whole form, not one field.
      const map: Record<string, Path<Values>> = { credentials: "root.server" as Path<Values> };
      if (!applyFieldErrors(error, form.setError, map))
        toastApiError(error, { requiredRole: "mpesa-admin" });
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      {controlledOpen === undefined && (
        <DialogTrigger asChild>
          <Button variant="outline" size="sm">
            <KeyRound /> Update credentials
          </Button>
        </DialogTrigger>
      )}
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Update Daraja credentials</DialogTitle>
          <DialogDescription>
            Change these at Safaricom first (Daraja portal or M-Pesa org portal), then save the new
            values here. Saving re-checks the connection. Fill in only what changed; empty fields
            keep the stored value.
          </DialogDescription>
        </DialogHeader>
        {reason && <p className="rounded-md bg-info-soft px-3 py-2 text-info text-sm">{reason}</p>}
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              label="Consumer key"
              type="password"
              autoComplete="off"
              description={`Now ${credentials.consumer_key_masked}`}
              error={errors.consumer_key}
              {...form.register("consumer_key")}
            />
            <TextField
              label="Consumer secret"
              type="password"
              autoComplete="off"
              description={`Now ${credentials.consumer_secret_masked}`}
              error={errors.consumer_secret}
              {...form.register("consumer_secret")}
            />
            {passkey && (
              <TextField
                label="Lipa na M-Pesa passkey"
                type="password"
                autoComplete="off"
                className="sm:col-span-2"
                description={`${
                  credentials.passkey_masked ? `Now ${credentials.passkey_masked}` : "Not set"
                }${till && shortcode.store_number ? `. Issued for store ${shortcode.store_number}` : ""}. ${CREDENTIAL_HINTS.passkey}`}
                error={errors.passkey}
                {...form.register("passkey")}
              />
            )}
            {initiator && (
              <>
                <TextField
                  label="Initiator name"
                  autoComplete="off"
                  description={`${
                    credentials.initiator_name ? `Now ${credentials.initiator_name}` : "Not set"
                  }. ${initiatorUse(operations)}${roles.length > 0 ? ` Org-portal roles: ${roles.join(", ")}.` : ""}`}
                  error={errors.initiator_name}
                  {...form.register("initiator_name")}
                />
                <TextField
                  label="Initiator password"
                  type="password"
                  autoComplete="off"
                  description={`${credentials.initiator_password_set ? "Set" : "Not set"}.${
                    production ? ` ${CREDENTIAL_HINTS.productionCertificate}` : ""
                  }`}
                  error={errors.initiator_password}
                  {...form.register("initiator_password")}
                />
              </>
            )}
          </div>
          {initiator && (
            <div className="flex flex-col gap-2 font-medium text-sm">
              <label htmlFor="update-security-credential">
                Security credential (instead of the password)
              </label>
              <Textarea
                id="update-security-credential"
                rows={3}
                autoComplete="off"
                className="font-mono text-xs placeholder:font-sans"
                placeholder="Generated on the Daraja portal; used instead of the initiator password"
                aria-invalid={errors.security_credential ? true : undefined}
                {...form.register("security_credential")}
              />
              {errors.security_credential && (
                <span className="font-normal text-destructive">
                  {errors.security_credential.message}
                </span>
              )}
            </div>
          )}
          <FormRootError message={errors.root?.server?.message ?? errors.root?.message} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting && <Spinner />}
              Save credentials
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
