"use client";

import type { ReactNode } from "react";
import { useHasRole } from "@/hooks/use-has-role";
import type { ConsoleRole } from "@/lib/roles";

type RoleGateProps = {
  /** Any one of these roles (or a role above it) lets the children render. */
  role: ConsoleRole | readonly ConsoleRole[];
  children: ReactNode;
  /** Rendered instead when the user lacks the role, e.g. a disabled button. */
  fallback?: ReactNode;
};

/** Hides controls the user cannot use. The API still enforces roles; this is only UI. */
export function RoleGate({ role, children, fallback = null }: RoleGateProps) {
  return useHasRole(role) ? children : fallback;
}
