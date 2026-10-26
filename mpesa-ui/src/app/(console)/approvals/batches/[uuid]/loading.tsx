import { TableSkeleton } from "@/components/skeletons";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

export default function BatchLoading() {
  return (
    <div aria-busy="true" className="flex flex-col gap-6">
      <span className="sr-only">Loading the bulk payout…</span>
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-40" />
        <Skeleton className="h-8 w-72 max-w-full" />
      </div>
      <Skeleton className="h-32 w-full rounded-xl" />
      <Card className="gap-4 p-5">
        <TableSkeleton rows={8} columns={6} />
      </Card>
    </div>
  );
}
