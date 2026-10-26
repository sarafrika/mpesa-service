import { ProductCardSkeleton } from "@/components/products/product-card";
import { TableSkeleton } from "@/components/skeletons";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

export default function ProductsLoading() {
  return (
    <div aria-busy="true" className="flex flex-col gap-6">
      <span className="sr-only">Loading products…</span>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-2">
          <Skeleton className="h-8 w-64" />
          <Skeleton className="h-4 w-96 max-w-full" />
        </div>
        <Skeleton className="h-9 w-40" />
      </div>
      <div className="grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(min(100%,400px),1fr))]">
        <ProductCardSkeleton />
        <ProductCardSkeleton />
      </div>
      <Card className="gap-4 p-5">
        <Skeleton className="h-5 w-56" />
        <TableSkeleton rows={5} columns={7} />
      </Card>
    </div>
  );
}
