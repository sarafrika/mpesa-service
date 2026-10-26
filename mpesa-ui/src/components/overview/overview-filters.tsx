"use client";

import type { ShortcodeSummary } from "@/api/generated/model";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { PERIODS, type PeriodKey } from "./period";
import type { OverviewFilters as Filters, OverviewEnvironment } from "./use-overview-filters";

const ALL = "all";

type OverviewFiltersProps = {
  filters: Filters;
  /** Shortcodes in the chosen environment, from the shortcode list; undefined while loading. */
  paybills: ShortcodeSummary[] | undefined;
  onPaybillChange: (paybill: string | undefined) => void;
  onPeriodChange: (period: PeriodKey) => void;
  onEnvironmentChange: (environment: OverviewEnvironment) => void;
};

export function OverviewFilters({
  filters,
  paybills,
  onPaybillChange,
  onPeriodChange,
  onEnvironmentChange,
}: OverviewFiltersProps) {
  const known = paybills?.some((paybill) => paybill.uuid === filters.paybill);
  return (
    <>
      <div className="flex min-w-48 flex-col gap-1.5">
        <Label htmlFor="overview-paybill" className="text-muted-foreground text-xs">
          Paybill
        </Label>
        <Select
          value={filters.paybill ?? ALL}
          onValueChange={(value) => onPaybillChange(value === ALL ? undefined : value)}
        >
          <SelectTrigger id="overview-paybill" className="w-full sm:w-56">
            <SelectValue placeholder="All paybills" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>
              All paybills{paybills ? ` (${paybills.length})` : ""}
            </SelectItem>
            {filters.paybill && !known && (
              <SelectItem value={filters.paybill}>Selected paybill</SelectItem>
            )}
            {paybills?.map((paybill) => (
              <SelectItem key={paybill.uuid} value={paybill.uuid}>
                {paybill.business_name} · {paybill.shortcode}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex min-w-36 flex-col gap-1.5">
        <Label htmlFor="overview-period" className="text-muted-foreground text-xs">
          Period
        </Label>
        <Select
          value={filters.period}
          onValueChange={(value) => onPeriodChange(value as PeriodKey)}
        >
          <SelectTrigger id="overview-period" className="w-full sm:w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {PERIODS.map((period) => (
              <SelectItem key={period.value} value={period.value}>
                {period.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex flex-col gap-1.5">
        <span id="overview-environment" className="font-medium text-muted-foreground text-xs">
          Environment
        </span>
        <ToggleGroup
          type="single"
          aria-labelledby="overview-environment"
          value={filters.environment}
          onValueChange={(value) => {
            if (value) onEnvironmentChange(value as OverviewEnvironment);
          }}
          spacing={1}
          className="rounded-lg border bg-muted p-0.5"
        >
          <ToggleGroupItem
            value="SANDBOX"
            className="h-8 px-3 data-[state=on]:bg-background data-[state=on]:shadow-xs"
          >
            Sandbox
          </ToggleGroupItem>
          <ToggleGroupItem
            value="PRODUCTION"
            className="h-8 px-3 data-[state=on]:bg-background data-[state=on]:shadow-xs"
          >
            Production
          </ToggleGroupItem>
        </ToggleGroup>
      </div>
    </>
  );
}
