export type DiffChange = "added" | "removed" | "changed" | "same";

export type DiffRow = { path: string; before?: string; after?: string; change: DiffChange };

function show(value: unknown): string {
  if (value === null) return "null";
  if (typeof value === "string") return value;
  return JSON.stringify(value);
}

/** Leaf values by path: `credentials.consumer_key`, `items[0].amount`; a bare value is `(value)`. */
export function flatten(value: unknown, prefix = "", into = new Map<string, string>()) {
  if (Array.isArray(value) && value.length > 0) {
    value.forEach((item, index) => {
      flatten(item, `${prefix}[${index}]`, into);
    });
  } else if (value && typeof value === "object" && Object.keys(value).length > 0) {
    for (const [key, item] of Object.entries(value)) {
      flatten(item, prefix ? `${prefix}.${key}` : key, into);
    }
  } else if (value !== undefined) {
    into.set(prefix || "(value)", show(value));
  }
  return into;
}

/** Every path in either record, in the order first seen; a missing record (null) has no paths. */
export function diffRecords(before: unknown, after: unknown): DiffRow[] {
  const was = before == null ? new Map<string, string>() : flatten(before);
  const now = after == null ? new Map<string, string>() : flatten(after);
  const paths = [...new Set([...was.keys(), ...now.keys()])];
  return paths.map((path) => {
    const b = was.get(path);
    const a = now.get(path);
    const change: DiffChange =
      b === undefined ? "added" : a === undefined ? "removed" : a === b ? "same" : "changed";
    return { path, before: b, after: a, change };
  });
}
