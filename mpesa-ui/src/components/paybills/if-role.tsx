"use client";

import type { ReactNode } from "react";
import { RoleGate } from "@/components/role-gate";
import type { ConsoleRole } from "@/lib/roles";

/** RoleGate under a prop name Biome's ARIA `role` check leaves alone. */
export function IfRole({
  needs,
  children,
  fallback,
}: {
  needs: ConsoleRole;
  children: ReactNode;
  fallback?: ReactNode;
}) {
  return (
    <RoleGate role={needs} fallback={fallback}>
      {children}
    </RoleGate>
  );
}
