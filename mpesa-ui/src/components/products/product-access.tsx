"use client";

import { Boxes, Pencil, Plus } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import {
  useDeleteProductGrant,
  useListProductGrants,
  useListProducts,
  useUpsertProductGrant,
} from "@/api/generated/endpoints/admin-products/admin-products";
import type { ProductGrant } from "@/api/generated/model";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { SectionBoundary } from "@/components/error-boundary";
import { ErrorState } from "@/components/error-state";
import { RoleGate } from "@/components/role-gate";
import { SearchInput } from "@/components/search-input";
import { SectionCard } from "@/components/section-card";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { formatCount } from "@/lib/format";
import { PAGE_SIZES } from "@/lib/page";
import { cn } from "@/lib/utils";
import {
  OPERATION_HINTS,
  OPERATION_LABELS,
  OPERATIONS,
  type Operation,
  okData,
  webhookHealthCopy,
} from "./product-format";
import { useRefreshProducts } from "./use-refresh-products";

const NEW_PRODUCT = "__new__";
// The service caps a page at 100; past that the picker asks for a search instead of paging.
const PICKER_LIMIT = 100;

export type ProductAccessProps = { shortcodeUuid: string };

/** Which products may use this paybill on the machine API, and for which operations; admins edit. */
export function ProductAccess({ shortcodeUuid }: ProductAccessProps) {
  return (
    <SectionBoundary title="Product access">
      <ProductAccessCard shortcodeUuid={shortcodeUuid} />
    </SectionBoundary>
  );
}

// Unprefixed URL paging like the other paybill tabs, which clear page, size and sort on a tab switch.
function ProductAccessCard({ shortcodeUuid }: ProductAccessProps) {
  const paging = usePaging({ size: 10 });
  const [editing, setEditing] = useState<ProductGrant | null>(null);
  const [granting, setGranting] = useState(false);
  const grants = useListProductGrants(
    {
      shortcode_uuid: shortcodeUuid,
      page: paging.page,
      size: paging.size,
      sort: ["display_name,asc"],
    },
    { query: pagedQueryOptions },
  );
  const data = okData(grants.data);
  useClampPage(data, paging.page, paging.setPage);

  return (
    <SectionCard
      title="Product access"
      description="Which Sarafrika products may call the machine API with this paybill, and for which operations."
      actions={
        <RoleGate needs="mpesa-admin">
          <Button variant="outline" size="sm" onClick={() => setGranting(true)}>
            <Plus /> Grant a product
          </Button>
        </RoleGate>
      }
    >
      <StaleNotice query={grants} />
      {grants.isPending ? (
        <div aria-busy="true" className="flex flex-col gap-2">
          <span className="sr-only">Loading product access…</span>
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : grants.isError && !data ? (
        <ErrorState
          error={grants.error}
          requiredRole="mpesa-viewer"
          onRetry={() => grants.refetch()}
          retrying={grants.isFetching}
        />
      ) : !data || data.items.length === 0 ? (
        <EmptyState
          icon={Boxes}
          title="No product may use this paybill"
          description="Products are refused on the machine API until they are granted operations here."
          action={
            <RoleGate needs="mpesa-admin">
              <Button size="sm" onClick={() => setGranting(true)}>
                <Plus /> Grant a product
              </Button>
            </RoleGate>
          }
        />
      ) : (
        <>
          <ul
            className={cn(
              "flex flex-col gap-2 transition-opacity",
              grants.isPlaceholderData && "opacity-60",
            )}
            aria-busy={grants.isPlaceholderData || undefined}
          >
            {data.items.map((grant) => (
              <AccessRow key={grant.uuid} grant={grant} onEdit={() => setEditing(grant)} />
            ))}
          </ul>
          {data.total_items > PAGE_SIZES[0] && (
            <DataTablePagination
              meta={data}
              onPageChange={paging.setPage}
              onPageSizeChange={paging.setSize}
              disabled={grants.isPlaceholderData}
            />
          )}
        </>
      )}
      {editing && (
        <EditAccessDialog
          grant={editing}
          shortcodeUuid={shortcodeUuid}
          onClose={() => setEditing(null)}
        />
      )}
      {granting && (
        <GrantProductDialog
          shortcodeUuid={shortcodeUuid}
          taken={data?.items.map((grant) => grant.client_id) ?? []}
          onClose={() => setGranting(false)}
        />
      )}
    </SectionCard>
  );
}

function AccessRow({ grant, onEdit }: { grant: ProductGrant; onEdit: () => void }) {
  const webhook = webhookHealthCopy(grant.webhook_health);
  return (
    <li className="flex flex-wrap items-center gap-3 rounded-lg border p-3">
      <span className="flex min-w-0 flex-[1_1_200px] flex-col gap-0.5">
        <Link href="/products" className="font-semibold text-sm hover:underline">
          {grant.product_name}
        </Link>
        <span className="break-all font-mono text-muted-foreground text-xs">
          azp: {grant.client_id}
        </span>
      </span>
      <span className="flex flex-wrap gap-1.5">
        {!grant.active && <StatusBadge status="Paused" tone="warning" />}
        {grant.operations.map((operation) => (
          <StatusBadge
            key={operation}
            status={OPERATION_LABELS[operation as Operation] ?? operation}
            tone="neutral"
          />
        ))}
      </span>
      <span className="text-muted-foreground text-sm">
        {grant.webhook_health ? `Webhook ${webhook.text.toLowerCase()}` : "No webhook"}
      </span>
      <RoleGate needs="mpesa-admin">
        <Button
          variant="ghost"
          size="sm"
          onClick={onEdit}
          aria-label={`Edit ${grant.product_name}'s access`}
        >
          <Pencil /> Edit
        </Button>
      </RoleGate>
    </li>
  );
}

function OperationChecks({
  value,
  onChange,
  disabled,
  idPrefix,
}: {
  value: Operation[];
  onChange: (next: Operation[]) => void;
  disabled?: boolean;
  idPrefix: string;
}) {
  return (
    <fieldset aria-label="Operations" className="m-0 flex flex-col gap-2.5 border-0 p-0">
      {OPERATIONS.map((operation) => {
        const id = `${idPrefix}-${operation}`;
        return (
          <label key={operation} htmlFor={id} className="flex cursor-pointer items-start gap-2.5">
            <Checkbox
              id={id}
              className="mt-0.5"
              checked={value.includes(operation)}
              disabled={disabled}
              onCheckedChange={(on) =>
                onChange(
                  OPERATIONS.filter((op) => (op === operation ? on === true : value.includes(op))),
                )
              }
            />
            <span className="flex flex-col">
              <span className="font-medium text-sm">{OPERATION_LABELS[operation]}</span>
              <span className="text-muted-foreground text-xs">{OPERATION_HINTS[operation]}</span>
            </span>
          </label>
        );
      })}
    </fieldset>
  );
}

function EditAccessDialog({
  grant,
  shortcodeUuid,
  onClose,
}: {
  grant: ProductGrant;
  shortcodeUuid: string;
  onClose: () => void;
}) {
  const [operations, setOperations] = useState<Operation[]>(grant.operations as Operation[]);
  const [active, setActive] = useState(grant.active);
  const [removing, setRemoving] = useState(false);
  const refresh = useRefreshProducts();
  const upsert = useUpsertProductGrant({ mutation: { meta: { requiredRole: "mpesa-admin" } } });
  const remove = useDeleteProductGrant({ mutation: { meta: { requiredRole: "mpesa-admin" } } });
  const busy = upsert.isPending || remove.isPending;

  async function save() {
    try {
      await upsert.mutateAsync({
        clientId: grant.client_id,
        shortcodeUuid,
        data: { operations, active },
      });
      await refresh();
      toast.success(`${grant.product_name}'s access saved`);
      onClose();
    } catch {
      // The mutation cache toasts the reason.
    }
  }

  return (
    <>
      <Dialog open={!removing} onOpenChange={(open) => !open && !busy && onClose()}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{grant.product_name}</DialogTitle>
            <DialogDescription className="font-mono text-xs">{grant.client_id}</DialogDescription>
          </DialogHeader>
          <OperationChecks
            value={operations}
            onChange={setOperations}
            disabled={busy}
            idPrefix="edit-access"
          />
          <label htmlFor="edit-access-active" className="flex items-center justify-between gap-3">
            <span className="flex flex-col">
              <span className="font-medium text-sm">Active</span>
              <span className="text-muted-foreground text-xs">
                Pausing refuses the product here without forgetting its operations.
              </span>
            </span>
            <Switch id="edit-access-active" checked={active} onCheckedChange={setActive} />
          </label>
          {operations.length === 0 && (
            <p className="text-sm text-warning">Choose an operation, or remove the access.</p>
          )}
          <DialogFooter className="sm:justify-between">
            <Button variant="destructive" onClick={() => setRemoving(true)} disabled={busy}>
              Remove access
            </Button>
            <div className="flex flex-col-reverse gap-2 sm:flex-row">
              <Button variant="outline" onClick={onClose} disabled={busy}>
                Cancel
              </Button>
              <Button onClick={save} disabled={busy || operations.length === 0}>
                {upsert.isPending && <Spinner />}
                Save
              </Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <ConfirmDialog
        open={removing}
        onOpenChange={(open) => !open && setRemoving(false)}
        title={`Remove ${grant.product_name}'s access?`}
        description="Its calls on this paybill are refused from now on, and it stops getting webhooks for it."
        confirmLabel="Remove access"
        destructive
        onConfirm={async () => {
          await remove.mutateAsync({ clientId: grant.client_id, shortcodeUuid });
          await refresh();
          toast.success(`${grant.product_name} can no longer use this paybill`);
          onClose();
        }}
      />
    </>
  );
}

function GrantProductDialog({
  shortcodeUuid,
  taken,
  onClose,
}: {
  shortcodeUuid: string;
  taken: string[];
  onClose: () => void;
}) {
  const [search, setSearch] = useState("");
  const query = useDebouncedValue(search.trim(), 300);
  const products = useListProducts(
    { size: PICKER_LIMIT, sort: ["name,asc"], ...(query ? { search: query } : {}) },
    { query: pagedQueryOptions },
  );
  const productPage = okData(products.data);
  const choices = (productPage?.items ?? []).filter(
    (product) => !taken.includes(product.client_id),
  );
  const searchable = Boolean(query) || (productPage?.total_items ?? 0) > PICKER_LIMIT;
  const [choice, setChoice] = useState<string>("");
  const [clientId, setClientId] = useState("");
  const [name, setName] = useState("");
  const [operations, setOperations] = useState<Operation[]>(["STATUS_LOOKUP"]);
  const [error, setError] = useState<string | null>(null);
  const refresh = useRefreshProducts();
  const upsert = useUpsertProductGrant({ mutation: { meta: { requiredRole: "mpesa-admin" } } });
  const isNew = choice === NEW_PRODUCT || (choice === "" && choices.length === 0);

  async function save() {
    const target = isNew ? clientId.trim() : choice;
    if (!target || (isNew && !/^[A-Za-z0-9._:-]+$/.test(target))) {
      setError(isNew ? "Enter the product's Keycloak client id" : "Choose a product");
      return;
    }
    try {
      await upsert.mutateAsync({
        clientId: target,
        shortcodeUuid,
        data: {
          operations,
          active: true,
          display_name: isNew && name.trim() ? name.trim() : undefined,
        },
      });
      await refresh();
      toast.success("Access granted");
      onClose();
    } catch {
      // The mutation cache toasts the reason.
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && !upsert.isPending && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Grant a product</DialogTitle>
          <DialogDescription>
            Choose a connected product, or name a new Keycloak client.
          </DialogDescription>
        </DialogHeader>
        {searchable && (
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="Search products by name or client id"
          />
        )}
        {productPage && productPage.total_items > productPage.items.length && (
          <p className="text-muted-foreground text-xs" aria-live="polite">
            Showing the first {formatCount(productPage.items.length)} of{" "}
            {formatCount(productPage.total_items)} products; search to find the others.
          </p>
        )}
        {products.isPending ? (
          <Skeleton className="h-9 w-full" />
        ) : (
          <Field data-invalid={Boolean(error && !isNew) || undefined}>
            <FieldLabel htmlFor="grant-product">Product</FieldLabel>
            <Select
              value={choice || (choices.length === 0 ? NEW_PRODUCT : "")}
              onValueChange={(value) => {
                setChoice(value);
                setError(null);
              }}
            >
              <SelectTrigger id="grant-product" className="w-full">
                <SelectValue placeholder="Choose a product" />
              </SelectTrigger>
              <SelectContent>
                {choices.map((product) => (
                  <SelectItem key={product.client_id} value={product.client_id}>
                    {product.display_name} ({product.client_id})
                  </SelectItem>
                ))}
                <SelectItem value={NEW_PRODUCT}>A new Keycloak client…</SelectItem>
              </SelectContent>
            </Select>
          </Field>
        )}
        {isNew && (
          <div className="grid gap-3 sm:grid-cols-2">
            <Field data-invalid={Boolean(error) || undefined}>
              <FieldLabel htmlFor="grant-client">Client id</FieldLabel>
              <Input
                id="grant-client"
                className="font-mono"
                placeholder="shop-api"
                value={clientId}
                aria-invalid={Boolean(error) || undefined}
                onChange={(event) => {
                  setClientId(event.target.value);
                  setError(null);
                }}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="grant-name">Display name</FieldLabel>
              <Input
                id="grant-name"
                placeholder="Sarafrika Shop"
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
            </Field>
          </div>
        )}
        <FieldError>{error}</FieldError>
        <OperationChecks
          value={operations}
          onChange={setOperations}
          disabled={upsert.isPending}
          idPrefix="grant-access"
        />
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={upsert.isPending}>
            Cancel
          </Button>
          <Button onClick={save} disabled={upsert.isPending || operations.length === 0}>
            {upsert.isPending && <Spinner />}
            Grant access
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
