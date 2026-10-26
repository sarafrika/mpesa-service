export const CONSOLE_ROLES = [
  "mpesa-viewer",
  "mpesa-operator",
  "mpesa-finance",
  "mpesa-admin",
] as const;

export type ConsoleRole = (typeof CONSOLE_ROLES)[number];

// Mirrors the service's role hierarchy: admin > finance > viewer and admin > operator > viewer.
const IMPLIES: Record<ConsoleRole, readonly ConsoleRole[]> = {
  "mpesa-admin": ["mpesa-finance", "mpesa-operator"],
  "mpesa-finance": ["mpesa-viewer"],
  "mpesa-operator": ["mpesa-viewer"],
  "mpesa-viewer": [],
};

function isConsoleRole(role: string): role is ConsoleRole {
  return (CONSOLE_ROLES as readonly string[]).includes(role);
}

/** Every console role the granted realm roles add up to, implied ones included. */
export function effectiveRoles(granted: readonly string[] | null | undefined): Set<ConsoleRole> {
  const result = new Set<ConsoleRole>();
  const pending = (granted ?? []).filter(isConsoleRole);
  for (let role = pending.pop(); role; role = pending.pop()) {
    if (result.has(role)) continue;
    result.add(role);
    pending.push(...IMPLIES[role]);
  }
  return result;
}

/** True when the granted roles include any of `required`, directly or through the hierarchy. */
export function hasRole(
  granted: readonly string[] | null | undefined,
  required: ConsoleRole | readonly ConsoleRole[],
): boolean {
  const roles = effectiveRoles(granted);
  const wanted: readonly ConsoleRole[] = typeof required === "string" ? [required] : required;
  return wanted.some((role) => roles.has(role));
}
