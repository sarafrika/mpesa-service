"use client";

import { useHasRole } from "@/hooks/use-has-role";
import { maskPhone } from "@/lib/format";
import { cn } from "@/lib/utils";

type MaskedPhoneProps = { phone: string | null | undefined; className?: string };

/** Customer MSISDN, shown in full only to finance and admin users. */
export function MaskedPhone({ phone, className }: MaskedPhoneProps) {
  const canSee = useHasRole("mpesa-finance");
  return (
    <span className={cn("font-mono tabular-nums", className)}>
      {canSee && phone ? phone : maskPhone(phone)}
    </span>
  );
}
