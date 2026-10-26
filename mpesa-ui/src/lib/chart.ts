import type { ChartConfig } from "@/components/ui/chart";

// Money in is always --chart-1 (green) and money out --chart-2 (blue), in light and dark.
export const MONEY_IN = "var(--chart-1)";
export const MONEY_OUT = "var(--chart-2)";

export const moneyChartConfig = {
  collections: { label: "Collections", color: MONEY_IN },
  payouts: { label: "Payouts", color: MONEY_OUT },
} satisfies ChartConfig;
