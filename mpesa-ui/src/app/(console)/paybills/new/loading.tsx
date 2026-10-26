import { FormSkeleton } from "@/components/skeletons";
import { Skeleton } from "@/components/ui/skeleton";

export default function AddPaybillLoading() {
  return (
    <div aria-busy="true" className="flex flex-col gap-6">
      <span className="sr-only">Loading the add paybill form…</span>
      <div className="flex flex-col gap-2">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-4 w-80 max-w-full" />
      </div>
      <div className="grid gap-6 lg:grid-cols-[240px_minmax(0,1fr)]">
        <div className="flex flex-col gap-3">
          {Array.from({ length: 4 }, (_, index) => (
            // biome-ignore lint/suspicious/noArrayIndexKey: placeholder steps have no identity
            <Skeleton key={index} className="h-12 w-full rounded-lg" />
          ))}
        </div>
        <FormSkeleton fields={6} className="rounded-xl border p-6" />
      </div>
    </div>
  );
}
