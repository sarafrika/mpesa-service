import { StatRowSkeleton, TableSkeleton } from "@/components/skeletons";
import { Skeleton } from "@/components/ui/skeleton";

export default function ReconciliationLoading() {
  return (
    <div className="flex flex-col gap-6" aria-busy>
      <span className="sr-only">Loading reconciliation</span>
      <div className="flex flex-col gap-2">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-96 max-w-full" />
      </div>
      <Skeleton className="h-44 w-full rounded-xl" />
      <StatRowSkeleton count={4} />
      <TableSkeleton rows={6} columns={7} />
    </div>
  );
}
