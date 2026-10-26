"use client";

import { useSession } from "next-auth/react";
import { type ConsoleRole, hasRole } from "@/lib/roles";

/** True once the session has loaded and grants any of `role`, through the hierarchy too. */
export function useHasRole(role: ConsoleRole | readonly ConsoleRole[]): boolean {
  const { data: session } = useSession();
  return hasRole(session?.roles, role);
}
