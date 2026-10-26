import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export type TimelineTone = "success" | "warning" | "danger" | "info" | "muted";

export type TimelineEvent = {
  id?: string;
  title: ReactNode;
  /** When it happened, already formatted (e.g. with `formatDateTime`). */
  time?: ReactNode;
  description?: ReactNode;
  tone?: TimelineTone;
};

const DOT: Record<TimelineTone, string> = {
  success: "bg-success",
  warning: "bg-warning",
  danger: "bg-destructive",
  info: "bg-info",
  muted: "bg-muted-foreground",
};

type TimelineProps = { events: readonly TimelineEvent[]; className?: string };

/** Vertical list of events with a coloured dot each, oldest first. */
export function Timeline({ events, className }: TimelineProps) {
  return (
    <ol className={cn("flex flex-col", className)}>
      {events.map((event, index) => (
        <li key={event.id ?? index} className="flex gap-3">
          <span className="flex flex-col items-center" aria-hidden>
            <span className={cn("mt-1 size-2.5 rounded-full", DOT[event.tone ?? "muted"])} />
            {index < events.length - 1 && <span className="min-h-5 w-0.5 flex-1 bg-border" />}
          </span>
          <span className="flex min-w-0 flex-col gap-0.5 pb-3">
            <span className="font-semibold text-sm">{event.title}</span>
            {event.description && (
              <span className="text-muted-foreground text-sm">{event.description}</span>
            )}
            {event.time && <span className="text-muted-foreground text-xs">{event.time}</span>}
          </span>
        </li>
      ))}
    </ol>
  );
}
