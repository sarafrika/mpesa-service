import { z } from "zod";
import type { UpdateValidationRulesRequest, ValidationRulesDto } from "@/api/generated/model";

const amount = z
  .string()
  .trim()
  .refine((value) => value === "" || /^\d+(\.\d{1,2})?$/.test(value.replaceAll(",", "")), {
    message: "Enter an amount like 250000 or 10.50",
  })
  .refine((value) => value === "" || Number(value.replaceAll(",", "")) >= 1, {
    message: "Must be at least KES 1",
  });

export const validationRulesSchema = z
  .object({
    active: z.boolean(),
    account_pattern: z.string().trim().max(200, "Keep the pattern to 200 characters or fewer"),
    min_amount: amount,
    max_amount: amount,
    use_lookup: z.boolean(),
    lookup_url: z.string().trim().max(500),
    lookup_timeout_ms: z
      .string()
      .trim()
      .refine((value) => /^\d+$/.test(value) && Number(value) >= 100 && Number(value) <= 5000, {
        message: "Between 100 and 5000 ms",
      }),
    on_lookup_failure: z.enum(["ACCEPT", "REJECT"]),
  })
  .superRefine((values, ctx) => {
    const min = toNumber(values.min_amount);
    const max = toNumber(values.max_amount);
    if (min !== undefined && max !== undefined && min > max) {
      ctx.addIssue({
        code: "custom",
        path: ["max_amount"],
        message: "Must not be below the minimum",
      });
    }
    if (values.use_lookup && !/^https?:\/\/\S+$/.test(values.lookup_url)) {
      ctx.addIssue({
        code: "custom",
        path: ["lookup_url"],
        message: "Enter the product's http(s) URL",
      });
    }
  });

export type ValidationRulesValues = z.infer<typeof validationRulesSchema>;

function toNumber(value: string): number | undefined {
  const cleaned = value.replaceAll(",", "").trim();
  if (!cleaned) return undefined;
  const number = Number(cleaned);
  return Number.isFinite(number) ? number : undefined;
}

/** `accountNumbers: false` is a till: Buy Goods payments have no account, so no pattern is kept or sent. */
export type RuleOptions = { accountNumbers?: boolean };

export function fromDto(
  dto: ValidationRulesDto | undefined,
  { accountNumbers = true }: RuleOptions = {},
): ValidationRulesValues {
  return {
    // A shortcode without saved rules starts with them switched on, ready to fill in.
    active: dto?.configured ? dto.active : true,
    account_pattern: accountNumbers ? (dto?.account_pattern ?? "") : "",
    min_amount: dto?.min_amount != null ? String(dto.min_amount) : "",
    max_amount: dto?.max_amount != null ? String(dto.max_amount) : "",
    use_lookup: Boolean(dto?.lookup_url),
    lookup_url: dto?.lookup_url ?? "",
    lookup_timeout_ms: String(dto?.lookup_timeout_ms ?? 2000),
    on_lookup_failure: dto?.on_lookup_failure ?? "REJECT",
  };
}

export function toRequest(
  values: ValidationRulesValues,
  { accountNumbers = true }: RuleOptions = {},
): UpdateValidationRulesRequest {
  return {
    active: values.active,
    account_pattern: accountNumbers ? values.account_pattern.trim() || undefined : undefined,
    min_amount: toNumber(values.min_amount),
    max_amount: toNumber(values.max_amount),
    lookup_url: values.use_lookup ? values.lookup_url.trim() || undefined : undefined,
    lookup_timeout_ms: Number(values.lookup_timeout_ms),
    on_lookup_failure: values.on_lookup_failure,
  };
}

/** What a dry run tried, as the service reports it: "ELM-000000 for KES 1", or no account on a till. */
export function describeTried(result: { account?: string; amount?: number }): string {
  const amount =
    result.amount != null
      ? `KES ${result.amount.toLocaleString("en-KE", { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`
      : "an amount";
  return result.account ? `${result.account} for ${amount}` : `A payment of ${amount}, no account`;
}

/** One line per rule, like the paybill's C2B section shows them. */
export function describeRules(
  values: ValidationRulesValues,
  { accountNumbers = true }: RuleOptions = {},
): string[] {
  if (!values.active) return ["Validation is off: every payment is accepted"];
  const lines: string[] = [];
  if (accountNumbers && values.account_pattern.trim())
    lines.push(`Account number matches ${values.account_pattern.trim()}`);
  const min = toNumber(values.min_amount);
  const max = toNumber(values.max_amount);
  if (min !== undefined || max !== undefined) {
    const kes = (n: number) => `KES ${n.toLocaleString("en-KE")}`;
    lines.push(
      min !== undefined && max !== undefined
        ? `Amount between ${kes(min)} and ${kes(max)}`
        : min !== undefined
          ? `Amount at least ${kes(min)}`
          : `Amount at most ${kes(max as number)}`,
    );
  }
  if (values.use_lookup && values.lookup_url.trim()) {
    lines.push(
      `Ask the product to confirm the ${accountNumbers ? "account" : "payment"} (timeout ${Number(values.lookup_timeout_ms) / 1000} s, ` +
        `${values.on_lookup_failure === "ACCEPT" ? "accept" : "reject"} if it can't answer)`,
    );
  }
  if (lines.length === 0) lines.push("No limits set: every payment is accepted");
  return lines;
}
