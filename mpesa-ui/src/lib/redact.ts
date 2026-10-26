import { maskPhone } from "./format.ts";

type Rule = "phone" | "initial" | "name" | "party";

// Keys (lower case, letters and digits only) that carry a customer's phone or name in Daraja
// callbacks and webhook bodies; `Name`/`Key` + `Value` items are matched on their name.
const RULES: Record<string, Rule> = {
  msisdn: "phone",
  phone: "phone",
  phonenumber: "phone",
  recipientphonenumber: "phone",
  firstname: "initial",
  middlename: "initial",
  lastname: "name",
  customername: "name",
  recipientname: "name",
  receiverpartypublicname: "party",
};

// Daraja's `…PublicName` / `…PartyName` values read `254722000000 - Jane Doe` (B2C, reversal, status).
const ruleFor = (key: string): Rule | undefined => {
  const normalised = key.toLowerCase().replace(/[^a-z0-9]/g, "");
  return RULES[normalised] ?? (/(publicname|partyname)$/.test(normalised) ? "party" : undefined);
};

const initial = (word: string) => (word ? `${word[0].toUpperCase()}.` : word);

/** `J. Wanjiku` for `John Kamau Wanjiku`: initials for all but the last name. */
export function shortenName(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length <= 1) return words[0] ?? "";
  return [...words.slice(0, -1).map(initial), words[words.length - 1]].join(" ");
}

const maskDigits = (text: string) => text.replace(/\+?\d{9,}/g, (digits) => maskPhone(digits));

/** Masks phone-like numbers (whole tokens of 9+ digits) in free text, such as the inbox's ids-in-body
 * value; digits inside an id such as `ws_CO_191220191020363925` stay. */
export const maskPhoneLike = (text: string): string =>
  text.replace(/(?<![A-Za-z0-9_])\+?\d{9,}(?![A-Za-z0-9_])/g, (digits) => maskPhone(digits));

function redactValue(rule: Rule, value: unknown): unknown {
  if (value === null || value === undefined || value === "") return value;
  const text = String(value);
  switch (rule) {
    case "phone":
      return maskPhone(text);
    case "initial":
      return text
        .trim()
        .split(/\s+/)
        .map((word) => (word.endsWith(".") && word.length <= 2 ? word : initial(word)))
        .join(" ");
    case "name":
      return shortenName(maskDigits(text));
    case "party": {
      // B2C sends `254722000000 - John Doe`.
      const [who, ...rest] = text.split(" - ");
      return rest.length
        ? `${maskDigits(who)} - ${shortenName(rest.join(" - "))}`
        : maskDigits(who);
    }
  }
}

/** A copy of a JSON value with customer phones masked and names shortened, for printing raw bodies. */
export function redactPersonalData(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(redactPersonalData);
  if (value === null || typeof value !== "object") return value;
  const record = value as Record<string, unknown>;
  const itemName = typeof record.Name === "string" ? record.Name : record.Key;
  const itemRule =
    typeof itemName === "string" && "Value" in record ? ruleFor(itemName) : undefined;
  const out: Record<string, unknown> = {};
  for (const [key, child] of Object.entries(record)) {
    const rule = key === "Value" && itemRule ? itemRule : ruleFor(key);
    out[key] =
      rule && typeof child !== "object" ? redactValue(rule, child) : redactPersonalData(child);
  }
  return out;
}
