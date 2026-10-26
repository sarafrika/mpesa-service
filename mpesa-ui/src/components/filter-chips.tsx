"use client";

import { cn } from "@/lib/utils";

export type ChipOption<T extends string> = { value: T; label: string; count?: number };

type FilterChipsProps<T extends string> = {
  options: readonly ChipOption<T>[];
  value: T;
  onChange: (value: T) => void;
  /** Names the group for screen readers, e.g. "Transaction type". */
  label: string;
  className?: string;
};

/** Single-select pill buttons; the selected one is filled and `aria-pressed`. */
export function FilterChips<T extends string>({
  options,
  value,
  onChange,
  label,
  className,
}: FilterChipsProps<T>) {
  return (
    <fieldset aria-label={label} className={cn("m-0 flex flex-wrap gap-2 border-0 p-0", className)}>
      {options.map((option) => {
        const on = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={on}
            onClick={() => onChange(option.value)}
            className={cn(
              "inline-flex min-h-8 items-center gap-1.5 rounded-full border px-3 font-medium text-sm outline-none transition-colors focus-visible:ring-3 focus-visible:ring-ring/50",
              on
                ? "border-foreground bg-foreground text-background"
                : "border-border bg-card text-foreground/80 hover:bg-muted",
            )}
          >
            {option.label}
            {option.count !== undefined && (
              <span
                className={cn("tabular-nums", on ? "text-background/70" : "text-muted-foreground")}
              >
                {option.count.toLocaleString("en-KE")}
              </span>
            )}
          </button>
        );
      })}
    </fieldset>
  );
}
