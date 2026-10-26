import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { BadgeTone } from "./approval-format";

const TONES: Record<BadgeTone, string> = {
  success: "border-success/30 bg-success-soft text-success",
  warning: "border-warning/30 bg-warning-soft text-warning",
  danger: "border-destructive/30 bg-destructive-soft text-destructive",
  info: "border-info/30 bg-info-soft text-info",
  neutral: "border-border bg-muted text-muted-foreground",
};

/** A word and a colour like StatusBadge, keeping the label as written (B2B, OK). */
export function ToneBadge({ label, tone }: { label: string; tone: BadgeTone }) {
  return (
    <Badge variant="outline" className={cn("gap-1.5", TONES[tone])}>
      <span aria-hidden className="size-1.5 rounded-full bg-current" />
      {label}
    </Badge>
  );
}
