import type { ReactNode } from "react";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

export type NoteTone = "success" | "warning" | "danger" | "muted";

const NOTE_TONES: Record<NoteTone, string> = {
  success: "text-success",
  warning: "text-warning",
  danger: "text-destructive",
  muted: "text-muted-foreground",
};

type StatCardProps = {
  label: ReactNode;
  value: ReactNode;
  /** One line under the value, e.g. "▲ 11% vs last Monday". */
  note?: ReactNode;
  noteTone?: NoteTone;
  loading?: boolean;
  className?: string;
};

export function StatCard({
  label,
  value,
  note,
  noteTone = "muted",
  loading = false,
  className,
}: StatCardProps) {
  return (
    <Card className={cn("gap-1 px-5 py-4", className)} aria-busy={loading || undefined}>
      <span className="text-muted-foreground text-sm">{label}</span>
      {loading ? (
        <>
          <Skeleton className="my-1 h-7 w-32" />
          <Skeleton className="h-4 w-24" />
        </>
      ) : (
        <>
          <span className="font-semibold text-2xl tabular-nums tracking-tight">{value}</span>
          {note && <span className={cn("text-xs", NOTE_TONES[noteTone])}>{note}</span>}
        </>
      )}
    </Card>
  );
}
