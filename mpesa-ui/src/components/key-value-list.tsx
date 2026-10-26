import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export type KeyValueItem = {
  label: ReactNode;
  value: ReactNode;
  /** Geist Mono, for receipts, ids and shortcodes. */
  mono?: boolean;
};

type KeyValueListProps = { items: readonly KeyValueItem[]; className?: string };

/** Label/value grid used by detail panels; empty values show "—". */
export function KeyValueList({ items, className }: KeyValueListProps) {
  return (
    <dl
      className={cn(
        "grid grid-cols-[minmax(110px,170px)_1fr] gap-x-4 gap-y-2.5 text-sm",
        className,
      )}
    >
      {items.map((item, index) => (
        // biome-ignore lint/suspicious/noArrayIndexKey: rows are positional and labels may be nodes
        <div key={index} className="contents">
          <dt className="text-muted-foreground">{item.label}</dt>
          <dd
            className={cn("min-w-0 break-words font-medium", item.mono && "font-mono text-[13px]")}
          >
            {item.value ?? "—"}
          </dd>
        </div>
      ))}
    </dl>
  );
}
