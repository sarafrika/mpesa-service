"use client";

import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import { useConnectProduct } from "@/api/generated/endpoints/admin-products/admin-products";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldLabel, FieldTitle } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { applyFieldErrors } from "@/lib/api-errors";
import { GrantMatrix, type GrantSelection } from "./grant-matrix";
import { okData } from "./product-format";
import { SecretReveal, secretDialogGuards } from "./secret-reveal";
import { useRefreshProducts } from "./use-refresh-products";

type ConnectForm = {
  client_id: string;
  display_name: string;
  webhook_url: string;
  grants: GrantSelection;
};

const EMPTY: ConnectForm = { client_id: "", display_name: "", webhook_url: "", grants: {} };

// Per-grant errors (grants[0].shortcode_uuid) belong to the grant matrix as a whole.
const FIELD_MAP = { grants: "grants" } as const;

type ConnectProductDialogProps = { open: boolean; onOpenChange: (open: boolean) => void };

/** Grants a Keycloak client its paybills and operations, and shows the webhook secret once. */
export function ConnectProductDialog({ open, onOpenChange }: ConnectProductDialogProps) {
  const [secret, setSecret] = useState<string | null>(null);
  const refresh = useRefreshProducts();
  const form = useForm<ConnectForm>({ defaultValues: EMPTY });
  const connect = useConnectProduct({
    mutation: { meta: { requiredRole: "mpesa-admin" } },
  });

  function close(next: boolean) {
    if (connect.isPending) return;
    onOpenChange(next);
    if (!next) {
      form.reset(EMPTY);
      setSecret(null);
      connect.reset();
    }
  }

  async function submit(values: ConnectForm) {
    const grants = Object.entries(values.grants).map(([shortcode_uuid, operations]) => ({
      shortcode_uuid,
      operations,
    }));
    if (grants.length === 0) {
      form.setError("grants", { message: "Choose at least one operation on one paybill" });
      return;
    }
    try {
      const response = await connect.mutateAsync({
        data: {
          client_id: values.client_id.trim(),
          display_name: values.display_name.trim(),
          grants,
          webhook_url: values.webhook_url.trim() || undefined,
        },
      });
      await refresh();
      const body = okData(response);
      toast.success(`${body?.product.display_name ?? values.display_name} is connected`);
      if (body?.webhook_secret) setSecret(body.webhook_secret);
      else close(false);
    } catch (error) {
      // The mutation cache has already toasted the mapped message.
      applyFieldErrors(error, form.setError, FIELD_MAP);
    }
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-xl" {...(secret ? secretDialogGuards : {})}>
        {secret ? (
          <>
            <DialogHeader>
              <DialogTitle>Webhook signing secret</DialogTitle>
              <DialogDescription>
                The product is connected. Give its team this secret to verify our webhooks.
              </DialogDescription>
            </DialogHeader>
            <SecretReveal secret={secret} />
            <DialogFooter>
              <Button onClick={() => close(false)}>I&apos;ve stored it</Button>
            </DialogFooter>
          </>
        ) : (
          <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
            <DialogHeader>
              <DialogTitle>Connect a product</DialogTitle>
              <DialogDescription>
                Its Keycloak client (client credentials, with the gateway audience mapper) may then
                use only the paybills and operations you tick here.
              </DialogDescription>
            </DialogHeader>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field data-invalid={Boolean(form.formState.errors.client_id) || undefined}>
                <FieldLabel htmlFor="connect-client-id">Keycloak client id</FieldLabel>
                <Input
                  id="connect-client-id"
                  placeholder="elimika-backend"
                  autoComplete="off"
                  className="font-mono"
                  aria-invalid={Boolean(form.formState.errors.client_id) || undefined}
                  {...form.register("client_id", {
                    required: "Enter the client id",
                    pattern: {
                      value: /^[A-Za-z0-9._:-]+$/,
                      message: "Letters, digits, '.', '_', ':' and '-' only",
                    },
                    maxLength: { value: 100, message: "At most 100 characters" },
                  })}
                />
                <FieldError errors={[form.formState.errors.client_id]} />
              </Field>
              <Field data-invalid={Boolean(form.formState.errors.display_name) || undefined}>
                <FieldLabel htmlFor="connect-name">Display name</FieldLabel>
                <Input
                  id="connect-name"
                  placeholder="Elimika"
                  aria-invalid={Boolean(form.formState.errors.display_name) || undefined}
                  {...form.register("display_name", {
                    required: "Enter a name people recognise",
                    maxLength: { value: 255, message: "At most 255 characters" },
                  })}
                />
                <FieldError errors={[form.formState.errors.display_name]} />
              </Field>
            </div>
            <Field data-invalid={Boolean(form.formState.errors.grants) || undefined}>
              <FieldTitle id="connect-grants-label">Paybills and operations</FieldTitle>
              <Controller
                control={form.control}
                name="grants"
                render={({ field }) => (
                  <GrantMatrix
                    value={field.value}
                    onChange={(next) => {
                      field.onChange(next);
                      form.clearErrors("grants");
                    }}
                    disabled={connect.isPending}
                    errorId="connect-grants-error"
                    labelId="connect-grants-label"
                  />
                )}
              />
              <FieldError id="connect-grants-error" errors={[form.formState.errors.grants]} />
            </Field>
            <Field data-invalid={Boolean(form.formState.errors.webhook_url) || undefined}>
              <FieldLabel htmlFor="connect-webhook">Webhook URL (optional)</FieldLabel>
              <Input
                id="connect-webhook"
                type="url"
                inputMode="url"
                placeholder="https://api.elimika.sarafrika.com/hooks/mpesa"
                className="font-mono"
                aria-invalid={Boolean(form.formState.errors.webhook_url) || undefined}
                {...form.register("webhook_url", {
                  validate: (value) =>
                    !value.trim() ||
                    /^https:\/\//i.test(value.trim()) ||
                    /^http:\/\/(localhost|127\.0\.0\.1|\[::1\])([:/]|$)/i.test(value.trim()) ||
                    "Use https (http only for localhost)",
                })}
              />
              <FieldDescription>
                Payment events are signed with HMAC-SHA256; the secret is shown once after you
                connect.
              </FieldDescription>
              <FieldError errors={[form.formState.errors.webhook_url]} />
            </Field>
            {form.formState.errors.root?.server && (
              <p role="alert" className="text-destructive text-sm">
                {form.formState.errors.root.server.message}
              </p>
            )}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => close(false)}
                disabled={connect.isPending}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={connect.isPending}>
                {connect.isPending && <Spinner />}
                Connect product
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
