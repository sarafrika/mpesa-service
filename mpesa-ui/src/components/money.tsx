import { formatKes } from "@/lib/format";
import { cn } from "@/lib/utils";

type MoneyProps = {
  amount: number | string | null | undefined;
  /** Money in shows `+` in --chart-1, money out `−` in --chart-2. */
  direction?: "in" | "out";
  /** Prefix `+` on positive amounts without colouring them. */
  signed?: boolean;
  /** Leave out "KES" when the column header names the currency. */
  hideCode?: boolean;
  /** Round to whole shillings for headline figures. */
  whole?: boolean;
  className?: string;
};

export function Money({ amount, direction, signed, hideCode, whole, className }: MoneyProps) {
  const number = typeof amount === "string" ? Number(amount) : amount;
  const value =
    direction && typeof number === "number" && Number.isFinite(number)
      ? direction === "in"
        ? Math.abs(number)
        : -Math.abs(number)
      : amount;
  return (
    <span
      className={cn(
        "whitespace-nowrap tabular-nums",
        direction === "in" && "text-chart-1",
        direction === "out" && "text-chart-2",
        className,
      )}
    >
      {formatKes(value, { signed: signed || Boolean(direction), hideCode, whole })}
    </span>
  );
}
