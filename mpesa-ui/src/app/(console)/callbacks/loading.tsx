import { StatRowSkeleton, TableSkeleton } from "@/components/skeletons";
import { Skeleton } from "@/components/ui/skeleton";

export default function CallbacksLoading() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-80 max-w-full" />
      </div>
      <StatRowSkeleton count={3} />
      <Skeleton className="h-28 w-full rounded-xl" />
      <TableSkeleton rows={10} columns={6} />
    </div>
  );
}
