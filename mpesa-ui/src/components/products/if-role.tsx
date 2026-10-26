"use client";

import type { ReactNode } from "react";
import { useHasRole } from "@/hooks/use-has-role";
import type { ConsoleRole } from "@/lib/roles";

// RoleGate's `role` prop trips Biome's ARIA role rule in JSX, so these screens gate with `need`.
export function IfRole({ need, children }: { need: ConsoleRole; children: ReactNode }) {
  return useHasRole(need) ? children : null;
}
