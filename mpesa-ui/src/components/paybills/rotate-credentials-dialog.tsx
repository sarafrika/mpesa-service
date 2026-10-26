"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { KeyRound } from "lucide-react";
import { useState } from "react";
import { type Path, useForm } from "react-hook-form";
import { toast } from "sonner";
import type { z } from "zod";
import { useRotateShortcodeCredentials } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import type { ShortcodeDetail } from "@/api/generated/model";
import { RotateShortcodeCredentialsBody } from "@/api/generated/zod/admin-shortcodes/admin-shortcodes.zod";
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
import { toastApiError, useInvalidateShortcodes } from "./api";
import { FormRootError, TextField } from "./form-fields";
import { snakeFieldMap } from "./paybill-health";

const FIELDS = [
  "consumer_key",
  "consumer_secret",
  "passkey",
  "initiator_name",
  "initiator_password",
  "security_credential",
] as const;

const schema = RotateShortcodeCredentialsBody.refine(
  (values) => FIELDS.some((field) => (values[field] ?? "").trim() !== ""),
  { path: ["root"], message: "Enter at least one new credential" },
);
type Values = z.infer<typeof schema>;
const EMPTY: Values = Object.fromEntries(FIELDS.map((field) => [field, ""])) as Values;

/** mpesa-admin only. Values are write-only: they leave the browser once and are never shown again. */
export function RotateCredentialsDialog({ shortcode }: { shortcode: ShortcodeDetail }) {
  const [open, setOpen] = useState(false);
  const invalidate = useInvalidateShortcodes();
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: EMPTY });
  const rotate = useRotateShortcodeCredentials({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;
  const b2c = shortcode.shortcode_type === "B2C";

  async function submit(values: Values) {
    const data = Object.fromEntries(
      FIELDS.map((field) => [field, values[field]?.trim() || undefined]),
    ) as Values;
    try {
      await rotate.mutateAsync({ uuid: shortcode.uuid, data });
      toast.success("Credentials rotated", {
        description: "Run the checks again so Daraja confirms the new credentials.",
      });
      await invalidate();
      setOpen(false);
    } catch (error) {
      // "credentials" (nothing entered) belongs to the whole form, not one field.
      const map: Record<string, Path<Values>> = {
        ...snakeFieldMap(FIELDS),
        credentials: "root.server" as Path<Values>,
      };
      if (!applyFieldErrors(error, form.setError, map))
        toastApiError(error, { requiredRole: "mpesa-admin" });
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (next) form.reset(EMPTY);
        setOpen(next);
      }}
    >
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <KeyRound /> Rotate…
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Rotate Daraja credentials</DialogTitle>
          <DialogDescription>
            Fill in only what changed; empty fields keep the stored value. The change is audited and
            the token check must pass again.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              label="Consumer key"
              type="password"
              autoComplete="off"
              className="font-mono"
              description={`Now ${shortcode.credentials.consumer_key_masked}`}
              error={errors.consumer_key}
              {...form.register("consumer_key")}
            />
            <TextField
              label="Consumer secret"
              type="password"
              autoComplete="off"
              description={`Now ${shortcode.credentials.consumer_secret_masked}`}
              error={errors.consumer_secret}
              {...form.register("consumer_secret")}
            />
            {!b2c && (
              <TextField
                label="Lipa na M-Pesa passkey"
                type="password"
                autoComplete="off"
                description={
                  shortcode.credentials.passkey_masked
                    ? `Now ${shortcode.credentials.passkey_masked}`
                    : "Not set"
                }
                error={errors.passkey}
                {...form.register("passkey")}
              />
            )}
            <TextField
              label="B2C initiator name"
              autoComplete="off"
              description={
                shortcode.credentials.initiator_name
                  ? `Now ${shortcode.credentials.initiator_name}`
                  : "Not set"
              }
              error={errors.initiator_name}
              {...form.register("initiator_name")}
            />
            <TextField
              label="Initiator password"
              type="password"
              autoComplete="off"
              description={shortcode.credentials.initiator_password_set ? "Set" : "Not set"}
              error={errors.initiator_password}
              {...form.register("initiator_password")}
            />
          </div>
          <div className="flex flex-col gap-2 font-medium text-sm">
            <label htmlFor="rotate-security-credential">Security credential (optional)</label>
            <Textarea
              id="rotate-security-credential"
              rows={3}
              autoComplete="off"
              className="font-mono text-xs"
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
          <FormRootError message={errors.root?.server?.message ?? errors.root?.message} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting && <Spinner />}
              Rotate credentials
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
