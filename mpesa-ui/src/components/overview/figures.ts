import type { NoteTone } from "@/components/stat-card";

export type Delta = { text: string; tone: NoteTone };

const percent = new Intl.NumberFormat("en-KE", { maximumFractionDigits: 1 });

/** `▲ 11%` / `▼ 4.2%` against the previous figure; null when there is nothing to compare with. */
export function percentDelta(
  current: number,
  previous: number,
  { upIsGood = true }: { upIsGood?: boolean } = {},
): Delta | null {
  if (!Number.isFinite(current) || !Number.isFinite(previous)) return null;
  if (previous === 0) return current === 0 ? { text: "No change", tone: "muted" } : null;
  const change = ((current - previous) / previous) * 100;
  if (Math.abs(change) < 0.05) return { text: "No change", tone: "muted" };
  const up = change > 0;
  return {
    text: `${up ? "▲" : "▼"} ${percent.format(Math.abs(change))}%`,
    tone: up === upIsGood ? "success" : "danger",
  };
}

/** `▲ 0.6 pts` between two rates in 0..1; null when either is missing. */
export function pointsDelta(
  current: number | null | undefined,
  previous: number | null | undefined,
): Delta | null {
  if (current == null || previous == null) return null;
  const change = (current - previous) * 100;
  if (Math.abs(change) < 0.05) return { text: "No change", tone: "muted" };
  return {
    text: `${change > 0 ? "▲" : "▼"} ${percent.format(Math.abs(change))} pts`,
    tone: change > 0 ? "success" : "danger",
  };
}

/** `97.8%` for a 0..1 rate; "—" when nothing settled. */
export function formatRate(rate: number | null | undefined): string {
  return rate == null ? "—" : `${percent.format(rate * 100)}%`;
}

const compact = new Intl.NumberFormat("en-KE", { notation: "compact", maximumFractionDigits: 1 });

/** Axis ticks: `0`, `950`, `1.2K`, `2.4M`. */
export function formatCompact(value: number): string {
  return compact.format(value);
}
