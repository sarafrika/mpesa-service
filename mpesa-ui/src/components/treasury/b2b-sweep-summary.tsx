import { KeyValueList } from "@/components/key-value-list";
import { Money } from "@/components/money";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { parseSweepPayload } from "./float";

type B2bSweepSummaryProps = {
  /** The `payload` of a B2B_SWEEP approval request, as the service stored it (any shape is tolerated). */
  payload: unknown;
  className?: string;
};

/** What a checker needs to judge a B2B sweep: where the money goes, how much, and the float it leaves. */
export function B2bSweepSummary({ payload, className }: B2bSweepSummaryProps) {
  const sweep = parseSweepPayload(payload);
  const belowLine =
    sweep.balanceAfter != null &&
    sweep.lowFloatThreshold != null &&
    sweep.balanceAfter < sweep.lowFloatThreshold;
  const command =
    sweep.commandId === "BusinessBuyGoods"
      ? "Till (BusinessBuyGoods)"
      : sweep.commandId === "BusinessPayBill"
        ? "Paybill (BusinessPayBill)"
        : sweep.commandId;

  return (
    <div className={cn("flex flex-col gap-3", className)}>
      <KeyValueList
        items={[
          {
            label: "From",
            value: sweep.shortcode
              ? `${sweep.businessName ?? "Shortcode"} · ${sweep.shortcode}`
              : sweep.businessName,
          },
          { label: "To", value: sweep.destinationName },
          {
            label: "Receiver",
            value: sweep.receiverShortcode
              ? `${sweep.receiverShortcode} · ${command ?? ""}`
              : command,
            mono: true,
          },
          { label: "Account reference", value: sweep.accountReference, mono: true },
          { label: "Amount", value: <Money amount={sweep.amount} /> },
          { label: "Reference", value: sweep.reference, mono: true },
          {
            label: "Utility balance",
            value:
              sweep.utilityBalance != null ? (
                <span>
                  <Money amount={sweep.utilityBalance} />
                  {sweep.utilityReportedAt && (
                    <span className="text-muted-foreground">
                      {" "}
                      as of {formatDateTime(sweep.utilityReportedAt)}
                    </span>
                  )}
                </span>
              ) : (
                "Not known when the sweep was requested"
              ),
          },
          {
            label: "Left after the sweep",
            value: sweep.balanceAfter != null ? <Money amount={sweep.balanceAfter} /> : undefined,
          },
          {
            label: "Low-float line",
            value:
              sweep.lowFloatThreshold != null ? (
                <Money amount={sweep.lowFloatThreshold} />
              ) : undefined,
          },
        ]}
      />
      {belowLine && (
        <p
          role="status"
          className="rounded-md border border-warning/30 bg-warning-soft px-3 py-2 text-warning text-sm"
        >
          This sweep leaves the utility account below the shortcode's low-float line.
        </p>
      )}
    </div>
  );
}
