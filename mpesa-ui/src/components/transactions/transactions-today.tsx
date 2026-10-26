"use client";

import { useEffect, useState } from "react";
import { useListTransactions } from "@/api/generated/endpoints/admin-transactions/admin-transactions";
import { nairobiMidnight } from "@/components/overview/period";
import { formatCount } from "@/lib/format";

/** ` · 1,204 today` (Nairobi day) after the header copy; nothing until the count is known. */
export function TransactionsToday() {
  // The clock is read after mount so the server render does not depend on it.
  const [from, setFrom] = useState<string | null>(null);
  useEffect(() => setFrom(new Date(nairobiMidnight(Date.now())).toISOString()), []);
  const query = useListTransactions(
    { page: 0, size: 1, from: from ?? undefined },
    { query: { enabled: from !== null } },
  );
  const total = query.data?.status === 200 ? query.data.data.total_items : undefined;
  if (total === undefined) return null;
  return <span> · {formatCount(total)} today</span>;
}
