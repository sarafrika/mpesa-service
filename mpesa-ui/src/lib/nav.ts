import { type ConsoleRole, hasRole } from "./roles.ts";

type Gated = { needs?: ConsoleRole | readonly ConsoleRole[] };

/** Nav groups with only the items the granted roles can open; a group left empty is dropped. */
export function visibleNav<I extends Gated, G extends { items: readonly I[] }>(
  groups: readonly G[],
  granted: readonly string[] | null | undefined,
): (G & { items: I[] })[] {
  return groups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.needs || hasRole(granted, item.needs)),
    }))
    .filter((group) => group.items.length > 0);
}
