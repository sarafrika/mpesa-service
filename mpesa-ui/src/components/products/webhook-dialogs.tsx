"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import {
  useCreateWebhookSubscription,
  useRotateWebhookSecret,
} from "@/api/generated/endpoints/admin-webhooks/admin-webhooks";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { applyFieldErrors } from "@/lib/api-errors";
import { okData } from "./product-format";
import { SecretReveal } from "./secret-reveal";
import { useRefreshProducts } from "./use-refresh-products";

function SecretDialog({ secret, onClose }: { secret: string | null; onClose: () => void }) {
  return (
    <Dialog open={secret !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Webhook signing secret</DialogTitle>
          <DialogDescription>Give the product&apos;s team this secret now.</DialogDescription>
        </DialogHeader>
        {secret && <SecretReveal secret={secret} />}
        <DialogFooter>
          <Button onClick={onClose}>I&apos;ve stored it</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

type RotateProps = {
  subscriptionUuid: string;
  productName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

/** Confirms, rotates, then shows the new secret once. The old secret stops signing at once. */
export function RotateSecretDialog({
  subscriptionUuid,
  productName,
  open,
  onOpenChange,
}: RotateProps) {
  const [secret, setSecret] = useState<string | null>(null);
  const refresh = useRefreshProducts();
  const rotate = useRotateWebhookSecret({ mutation: { meta: { requiredRole: "mpesa-admin" } } });

  return (
    <>
      <ConfirmDialog
        open={open}
        onOpenChange={onOpenChange}
        title={`Rotate the webhook secret for ${productName}?`}
        description="Deliveries are signed with the new secret straight away, so the product rejects them until its team installs it."
        confirmLabel="Rotate secret"
        destructive
        onConfirm={async () => {
          const response = await rotate.mutateAsync({ uuid: subscriptionUuid });
          await refresh();
          setSecret(okData(response)?.secret ?? null);
        }}
      />
      <SecretDialog secret={secret} onClose={() => setSecret(null)} />
    </>
  );
}

type AddWebhookProps = {
  clientId: string;
  productName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

type WebhookForm = { url: string };

/** Registers a webhook for a product that has none yet, then shows its secret once. */
export function AddWebhookDialog({ clientId, productName, open, onOpenChange }: AddWebhookProps) {
  const [secret, setSecret] = useState<string | null>(null);
  const refresh = useRefreshProducts();
  const form = useForm<WebhookForm>({ defaultValues: { url: "" } });
  const create = useCreateWebhookSubscription({
    mutation: { meta: { requiredRole: "mpesa-admin" } },
  });

  function change(next: boolean) {
    if (create.isPending) return;
    onOpenChange(next);
    if (!next) form.reset({ url: "" });
  }

  async function submit(values: WebhookForm) {
    try {
      const response = await create.mutateAsync({
        data: { client_id: clientId, url: values.url.trim() },
      });
      await refresh();
      toast.success(`Webhook registered for ${productName}`);
      change(false);
      setSecret(okData(response)?.secret ?? null);
    } catch (error) {
      applyFieldErrors(error, form.setError, { url: "url" });
    }
  }

  const urlError = form.formState.errors.url;
  return (
    <>
      <Dialog open={open} onOpenChange={change}>
        <DialogContent className="sm:max-w-lg">
          <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
            <DialogHeader>
              <DialogTitle>Add a webhook</DialogTitle>
              <DialogDescription>
                Where {productName} receives signed payment events it is entitled to.
              </DialogDescription>
            </DialogHeader>
            <Field data-invalid={Boolean(urlError) || undefined}>
              <FieldLabel htmlFor="webhook-url">URL</FieldLabel>
              <Input
                id="webhook-url"
                type="url"
                inputMode="url"
                className="font-mono"
                placeholder="https://api.example.sarafrika.com/hooks/mpesa"
                aria-invalid={Boolean(urlError) || undefined}
                {...form.register("url", {
                  required: "Enter the URL",
                  validate: (value) =>
                    /^https:\/\//i.test(value.trim()) ||
                    /^http:\/\/(localhost|127\.0\.0\.1|\[::1\])([:/]|$)/i.test(value.trim()) ||
                    "Use https (http only for localhost)",
                })}
              />
              <FieldDescription>
                Retries at 10 s, 1 min, 10 min and 1 h, then it fails.
              </FieldDescription>
              <FieldError errors={[urlError]} />
            </Field>
            {form.formState.errors.root?.server && (
              <p role="alert" className="text-destructive text-sm">
                {form.formState.errors.root.server.message}
              </p>
            )}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => change(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={create.isPending}>
                {create.isPending && <Spinner />}
                Add webhook
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
      <SecretDialog secret={secret} onClose={() => setSecret(null)} />
    </>
  );
}
