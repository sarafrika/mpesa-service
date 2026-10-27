import { Badge } from "@/components/ui/badge";
import { statusLabel, statusTone, type Tone } from "@/lib/status";
import { cn } from "@/lib/utils";

const TONES: Record<Tone, string> = {
  success: "border-success/30 bg-success-soft text-success",
  warning: "border-warning/30 bg-warning-soft text-warning",
  danger: "border-destructive/30 bg-destructive-soft text-destructive",
  info: "border-info/30 bg-info-soft text-info",
  neutral: "border-border bg-muted text-muted-foreground",
};

export type { Tone };

/** A status is always a word and a colour, so it reads without colour too; unknown ones read neutral. */
export function StatusBadge({ status, tone }: { status: string; tone?: Tone }) {
  return (
    <Badge variant="outline" className={cn("gap-1.5", TONES[tone ?? statusTone(status)])}>
      <span aria-hidden className="size-1.5 rounded-full bg-current" />
      {statusLabel(status)}
    </Badge>
  );
}
