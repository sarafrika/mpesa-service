import { KeyValueList } from "@/components/key-value-list";
import { Money } from "@/components/money";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { parseSweepPayload, TOP_UP_COMMAND } from "./float";

type B2bSweepSummaryProps = {
  /** The `payload` of a B2B_SWEEP approval request, as the service stored it (any shape is tolerated). */
  payload: unknown;
  className?: string;
};

const COMMANDS: Record<string, string> = {
  BusinessBuyGoods: "Till (BusinessBuyGoods)",
  BusinessPayBill: "Paybill (BusinessPayBill)",
  [TOP_UP_COMMAND]: "B2C top-up (BusinessPayToBulk)",
};

/** What a checker needs to judge a sweep or B2C top-up: where the money goes, how much, and the Working
 * balance it is paid from. */
export function B2bSweepSummary({ payload, className }: B2bSweepSummaryProps) {
  const sweep = parseSweepPayload(payload);
  const topUp = sweep.commandId === TOP_UP_COMMAND;
  const short = sweep.balanceAfter != null && sweep.balanceAfter < 0;
  const command = sweep.commandId ? (COMMANDS[sweep.commandId] ?? sweep.commandId) : undefined;

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
              ? `${sweep.receiverShortcode}${sweep.receiverBusinessName ? ` · ${sweep.receiverBusinessName}` : ""} · ${command ?? ""}`
              : command,
            mono: true,
          },
          {
            label: topUp ? "Reference sent" : "Account reference",
            value: topUp ? sweep.reference : sweep.accountReference,
            mono: true,
          },
          { label: "Amount", value: <Money amount={sweep.amount} /> },
          { label: "Reference", value: sweep.reference, mono: true },
          {
            label: "Working balance",
            value:
              sweep.workingBalance != null ? (
                <span>
                  <Money amount={sweep.workingBalance} />
                  {sweep.workingReportedAt && (
                    <span className="text-muted-foreground">
                      {" "}
                      as of {formatDateTime(sweep.workingReportedAt)}
                    </span>
                  )}
                </span>
              ) : (
                "No Working balance reading when this was requested"
              ),
          },
          {
            label: "Working left after",
            value: sweep.balanceAfter != null ? <Money amount={sweep.balanceAfter} /> : undefined,
          },
        ]}
      />
      {short && (
        <p
          role="status"
          className="rounded-md border border-warning/30 bg-warning-soft px-3 py-2 text-warning text-sm"
        >
          The amount is more than the Working balance last reported. Safaricom refuses it unless
          funds are moved from Utility to Working on the M-PESA org portal first.
        </p>
      )}
    </div>
  );
}
