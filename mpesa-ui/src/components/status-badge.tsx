import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

const TONES = {
  success: "border-success/30 bg-success-soft text-success",
  warning: "border-warning/30 bg-warning-soft text-warning",
  danger: "border-destructive/30 bg-destructive-soft text-destructive",
  info: "border-info/30 bg-info-soft text-info",
  neutral: "border-border bg-muted text-muted-foreground",
} as const;

export type Tone = keyof typeof TONES;

const STATUS_TONE: Record<string, Tone> = {
  SUCCESS: "success",
  PENDING: "warning",
  FAILED: "danger",
  CANCELLED: "neutral",
};

/** A status is always a word and a colour, so it reads without colour too. */
export function StatusBadge({ status, tone }: { status: string; tone?: Tone }) {
  const label = status.charAt(0) + status.slice(1).toLowerCase();
  return (
    <Badge
      variant="outline"
      className={cn("gap-1.5", TONES[tone ?? STATUS_TONE[status] ?? "neutral"])}
    >
      <span aria-hidden className="size-1.5 rounded-full bg-current" />
      {label}
    </Badge>
  );
}
