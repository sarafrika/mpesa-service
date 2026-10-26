"use client";

import { Boxes, Plus } from "lucide-react";
import { useState } from "react";
import { useListProducts } from "@/api/generated/endpoints/admin-products/admin-products";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { StaleNotice } from "@/components/stale-notice";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { pagedQueryOptions } from "@/hooks/use-paging";
import { ProductCard, ProductCardSkeleton } from "./product-card";
import { okData } from "./product-format";

const CARDS_PER_PAGE = 24;
const GRID = "grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(min(100%,400px),1fr))]";

// Cards page locally, not in the URL: the deliveries table below owns ?page=.
export function ProductList({ onConnect }: { onConnect: () => void }) {
  const [page, setPage] = useState(0);
  const products = useListProducts(
    { page, size: CARDS_PER_PAGE, sort: ["name,asc"] },
    { query: pagedQueryOptions },
  );
  const data = okData(products.data);

  if (products.isPending) {
    return (
      <div className={GRID} aria-busy="true">
        <span className="sr-only">Loading products…</span>
        <ProductCardSkeleton />
        <ProductCardSkeleton />
      </div>
    );
  }
  if (products.isError && !data) {
    return (
      <SectionCard title="Products">
        <ErrorState
          error={products.error}
          requiredRole="mpesa-viewer"
          onRetry={() => products.refetch()}
          retrying={products.isFetching}
        />
      </SectionCard>
    );
  }
  const items = data?.items ?? [];
  if (items.length === 0) {
    return (
      <SectionCard>
        <EmptyState
          icon={Boxes}
          title="No products connected yet"
          description="Connect a Sarafrika product to let its Keycloak client use your paybills."
          action={
            <RoleGate needs="mpesa-admin">
              <Button onClick={onConnect}>
                <Plus /> Connect a product
              </Button>
            </RoleGate>
          }
        />
      </SectionCard>
    );
  }

  return (
    <section aria-label="Products" className="flex flex-col gap-3">
      <StaleNotice query={products} />
      <div className={GRID}>
        {items.map((product) => (
          <ProductCard key={product.client_id} product={product} />
        ))}
        <NextProductCard onConnect={onConnect} />
      </div>
      {data && data.total_pages > 1 && (
        <DataTablePagination meta={data} onPageChange={setPage} disabled={products.isFetching} />
      )}
    </section>
  );
}

function NextProductCard({ onConnect }: { onConnect: () => void }) {
  return (
    <Card className="gap-3.5 border-2 border-dashed p-5">
      <h2 className="font-semibold text-lg tracking-tight">Connect the next Sarafrika product</h2>
      <p className="text-muted-foreground text-sm">
        Four steps, about 15 minutes. The product team gets a typed client generated from the same
        OpenAPI spec this console uses.
      </p>
      <ol className="flex list-decimal flex-col gap-2.5 pl-5 text-sm">
        <li>
          <strong>Create its Keycloak client</strong> (client credentials) with the gateway audience
          mapper.
        </li>
        <li>
          <strong>Grant paybills and operations</strong>: STK push, C2B feed, B2C payouts, status
          lookups.
        </li>
        <li>
          <strong>Register a webhook</strong> for results; Sarafrika signs every event with
          HMAC-SHA256.
        </li>
        <li>
          <strong>Share the API docs</strong> and run a sandbox payment end to end.
        </li>
      </ol>
      <RoleGate needs="mpesa-admin">
        <Button className="self-start" onClick={onConnect}>
          Start
        </Button>
      </RoleGate>
    </Card>
  );
}
