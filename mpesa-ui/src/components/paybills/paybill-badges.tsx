import { Check, CircleHelp, Minus, TriangleAlert, X } from "lucide-react";
import type { ShortcodeHealth, ShortcodeSummary } from "@/api/generated/model";
import { StatusBadge } from "@/components/status-badge";
import { cn } from "@/lib/utils";
import {
  type CheckState,
  displayStatus,
  ENVIRONMENT_LABELS,
  type HealthCheck,
  healthChecks,
} from "./paybill-health";

const CHECK_ICON = { ok: Check, fail: X, warn: TriangleAlert, na: Minus, unknown: CircleHelp };
const CHECK_TEXT: Record<CheckState, string> = {
  ok: "text-success",
  fail: "text-destructive",
  warn: "text-warning",
  na: "text-muted-foreground",
  unknown: "text-muted-foreground",
};
const CHECK_WORD: Record<CheckState, string> = {
  ok: "Passed",
  fail: "Failing",
  warn: "Needs attention",
  na: "Not applicable",
  unknown: "Not checked",
};

export function PaybillStatusBadge({
  row,
  now,
}: {
  row: Pick<ShortcodeSummary, "status" | "health">;
  now: number;
}) {
  const { label, tone } = displayStatus(row, now);
  return <StatusBadge status={label} tone={tone} />;
}

export function EnvironmentBadge({ environment }: { environment: string }) {
  return (
    <StatusBadge
      status={ENVIRONMENT_LABELS[environment] ?? environment}
      tone={environment === "SANDBOX" ? "info" : "neutral"}
    />
  );
}

/** Token · C2B · last payment as three icon-and-word marks for a table cell. */
export function InlineHealthChecks({ health, now }: { health: ShortcodeHealth; now: number }) {
  return (
    <span className="flex gap-3">
      {healthChecks(health, now).map((check) => {
        const Icon = CHECK_ICON[check.state];
        return (
          <span
            key={check.key}
            title={`${check.label}: ${check.detail}`}
            className={cn("inline-flex items-center gap-1 text-xs", CHECK_TEXT[check.state])}
          >
            <Icon className="size-3.5" aria-hidden />
            <span className="sr-only">
              {check.label}: {CHECK_WORD[check.state]}.
            </span>
            <span aria-hidden>{check.short}</span>
          </span>
        );
      })}
    </span>
  );
}

const CHECK_TONE = {
  ok: "success",
  fail: "danger",
  warn: "warning",
  na: "neutral",
  unknown: "neutral",
} as const;

/** One check as a small card: name, state badge, detail line. */
export function HealthCheckCard({ check, state }: { check: HealthCheck; state?: string }) {
  return (
    <div className="flex flex-col gap-1.5 rounded-lg border bg-muted/40 p-3.5">
      <div className="flex items-center justify-between gap-2">
        <span className="font-medium text-sm">{check.label}</span>
        <StatusBadge status={state ?? CHECK_WORD[check.state]} tone={CHECK_TONE[check.state]} />
      </div>
      <span className="text-muted-foreground text-sm">{check.detail}</span>
    </div>
  );
}
