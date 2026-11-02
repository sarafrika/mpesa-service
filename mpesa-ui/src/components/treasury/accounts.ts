// Pure helper for the Float tab's Accounts card; no React or "@/" imports so `pnpm test` can load it.

const KEY_KINDS = new Set(["utility", "working", "charges"]);

/** Utility, Working and Charges paid always; any other account only when it holds money or `all` is set. */
export function shownAccounts<T extends { kind: string; available: number | null }>(
  rows: readonly T[],
  all: boolean,
): T[] {
  if (all) return [...rows];
  return rows.filter((row) => KEY_KINDS.has(row.kind) || (row.available ?? 0) !== 0);
}
