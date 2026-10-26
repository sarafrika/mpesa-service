"use client";

import { useState } from "react";
import { toast } from "sonner";
import {
  useDeleteProductGrant,
  useUpsertProductGrant,
} from "@/api/generated/endpoints/admin-products/admin-products";
import type { Product } from "@/api/generated/model";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { GrantMatrix, type GrantSelection } from "./grant-matrix";
import type { Operation } from "./product-format";
import { useRefreshProducts } from "./use-refresh-products";

function selectionOf(product: Product): GrantSelection {
  const selection: GrantSelection = {};
  for (const grant of product.grants) {
    if (grant.operations.length > 0) selection[grant.shortcode_uuid] = [...grant.operations];
  }
  return selection;
}

function same(a: readonly string[] = [], b: readonly string[] = []) {
  return a.length === b.length && a.every((item) => b.includes(item));
}

type EditGrantsDialogProps = {
  product: Product;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

/** Changes which paybills a product may use and how; unticking a whole paybill removes the grant. */
export function EditGrantsDialog({ product, open, onOpenChange }: EditGrantsDialogProps) {
  const [selection, setSelection] = useState<GrantSelection>(() => selectionOf(product));
  const [name, setName] = useState(product.display_name);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const refresh = useRefreshProducts();
  const upsert = useUpsertProductGrant({ mutation: { meta: { requiredRole: "mpesa-admin" } } });
  const remove = useDeleteProductGrant({ mutation: { meta: { requiredRole: "mpesa-admin" } } });

  function change(next: boolean) {
    if (saving) return;
    if (next) {
      setSelection(selectionOf(product));
      setName(product.display_name);
      setError(null);
    }
    onOpenChange(next);
  }

  async function save() {
    if (!name.trim()) {
      setError("Enter a display name");
      return;
    }
    setSaving(true);
    const renamed = name.trim() !== product.display_name;
    const existing = new Map(product.grants.map((grant) => [grant.shortcode_uuid, grant]));
    try {
      let first = true;
      for (const [shortcodeUuid, operations] of Object.entries(selection)) {
        const before = existing.get(shortcodeUuid);
        if (before && same(before.operations, operations) && !(renamed && first)) continue;
        await upsert.mutateAsync({
          clientId: product.client_id,
          shortcodeUuid,
          data: {
            operations: operations as Operation[],
            active: before?.active ?? true,
            display_name: renamed ? name.trim() : undefined,
          },
        });
        first = false;
      }
      for (const grant of product.grants) {
        if (!selection[grant.shortcode_uuid]) {
          await remove.mutateAsync({
            clientId: product.client_id,
            shortcodeUuid: grant.shortcode_uuid,
          });
        }
      }
      toast.success(`Grants for ${name.trim()} saved`);
      onOpenChange(false);
    } catch {
      // The mutation cache toasts the reason; whatever saved before the failure stays saved.
    } finally {
      setSaving(false);
      await refresh();
    }
  }

  return (
    <Dialog open={open} onOpenChange={change}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Edit grants</DialogTitle>
          <DialogDescription>
            What <span className="font-mono">{product.client_id}</span> may do on each paybill.
          </DialogDescription>
        </DialogHeader>
        <Field data-invalid={Boolean(error) || undefined}>
          <FieldLabel htmlFor="grants-name">Display name</FieldLabel>
          <Input
            id="grants-name"
            value={name}
            onChange={(event) => {
              setName(event.target.value);
              setError(null);
            }}
            aria-invalid={Boolean(error) || undefined}
          />
          <FieldError>{error}</FieldError>
        </Field>
        <GrantMatrix value={selection} onChange={setSelection} disabled={saving} />
        {Object.keys(selection).length === 0 && product.grants.length > 0 && (
          <p className="text-sm text-warning">
            Saving with nothing ticked removes every grant; the product will be refused on all
            paybills.
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => change(false)} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={save} disabled={saving}>
            {saving && <Spinner />}
            Save grants
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
