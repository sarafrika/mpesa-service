"use client";

import { CalendarDays, X } from "lucide-react";
import { useState } from "react";
import type { DateRange } from "react-day-picker";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { fromDateParam, toDateParam } from "./transaction-filters";

type DateRangeFilterProps = {
  /** Inclusive local days as `yyyy-MM-dd`; both undefined means any date. */
  from?: string;
  to?: string;
  onChange: (range: { from?: string; to?: string }) => void;
};

const dayFormat = new Intl.DateTimeFormat("en-KE", { day: "numeric", month: "short" });

function daysAgo(days: number): Date {
  const today = new Date();
  return new Date(today.getFullYear(), today.getMonth(), today.getDate() - days);
}

const PRESETS = [
  { label: "Today", from: 0, to: 0 },
  { label: "Yesterday", from: 1, to: 1 },
  { label: "Last 7 days", from: 6, to: 0 },
  { label: "Last 30 days", from: 29, to: 0 },
] as const;

/** A button showing the chosen days that opens a range calendar with quick presets. */
export function DateRangeFilter({ from, to, onChange }: DateRangeFilterProps) {
  const [open, setOpen] = useState(false);
  const start = fromDateParam(from);
  const end = fromDateParam(to) ?? start;
  const selected: DateRange | undefined = start ? { from: start, to: end } : undefined;
  const label = !start
    ? "Any date"
    : end && toDateParam(end) !== toDateParam(start)
      ? `${dayFormat.format(start)} – ${dayFormat.format(end)}`
      : dayFormat.format(start);

  function choose(range: { from?: Date; to?: Date } | undefined) {
    onChange({
      from: range?.from ? toDateParam(range.from) : undefined,
      to: range?.to ? toDateParam(range.to) : undefined,
    });
  }

  return (
    <div className="flex items-center gap-1">
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button variant="outline" size="lg" className="min-w-36 justify-start font-normal">
            <CalendarDays aria-hidden />
            <span className="sr-only">Date range: </span>
            {label}
          </Button>
        </PopoverTrigger>
        <PopoverContent align="end" className="w-auto p-0">
          <div className="flex flex-col sm:flex-row">
            <div className="flex flex-wrap gap-1 border-b p-2 sm:flex-col sm:border-r sm:border-b-0">
              {PRESETS.map((preset) => (
                <Button
                  key={preset.label}
                  variant="ghost"
                  size="sm"
                  className="justify-start"
                  onClick={() => {
                    choose({ from: daysAgo(preset.from), to: daysAgo(preset.to) });
                    setOpen(false);
                  }}
                >
                  {preset.label}
                </Button>
              ))}
            </div>
            <Calendar
              mode="range"
              selected={selected}
              onSelect={(range) => choose(range)}
              defaultMonth={start}
              disabled={{ after: new Date() }}
              numberOfMonths={1}
            />
          </div>
        </PopoverContent>
      </Popover>
      {start && (
        <Button
          variant="ghost"
          size="icon"
          aria-label="Clear date range"
          onClick={() => onChange({ from: undefined, to: undefined })}
        >
          <X />
        </Button>
      )}
    </div>
  );
}
