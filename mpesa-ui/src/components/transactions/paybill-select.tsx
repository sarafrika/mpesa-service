"use client";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

export type PaybillOption = { uuid: string; label: string };

const ALL = "all";

type PaybillSelectProps = {
  options: readonly PaybillOption[];
  value?: string;
  onChange: (uuid: string | undefined) => void;
};

/** "All paybills" or one shortcode; the selected one stays listed even before options load. */
export function PaybillSelect({ options, value, onChange }: PaybillSelectProps) {
  const known = !value || options.some((option) => option.uuid === value);
  return (
    <Select
      value={value ?? ALL}
      onValueChange={(next) => onChange(next === ALL ? undefined : next)}
    >
      <SelectTrigger aria-label="Paybill" className="h-9 min-w-44">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL}>All paybills</SelectItem>
        {!known && value && <SelectItem value={value}>Selected paybill</SelectItem>}
        {options.map((option) => (
          <SelectItem key={option.uuid} value={option.uuid}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
