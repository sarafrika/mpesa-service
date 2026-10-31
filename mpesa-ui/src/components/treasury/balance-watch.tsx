"use client";

import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { useGetShortcodeBalance } from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import type { ShortcodeFloatStatusDto } from "@/api/generated/model";
import { okData, useInvalidateShortcodes } from "@/components/paybills/api";
import { querySettled } from "./float";

const POLL_MS = 3_000;
/** How long the page polls for Safaricom's answer before it stops and says so. */
const GIVE_UP_MS = 30_000;

type BalanceStatusQuery = ReturnType<typeof useGetShortcodeBalance>;

export type BalanceWatch = {
  status: BalanceStatusQuery;
  data: ShortcodeFloatStatusDto | undefined;
  /** True while the page waits for Safaricom's answer to a balance query. */
  waiting: boolean;
  /** Starts polling for the result of the query with this uuid. */
  watch: (queryUuid: string) => void;
};

const BalanceWatchContext = createContext<BalanceWatch | null>(null);

/** One balance status and one "waiting for Safaricom" state for the balance band and the Float tab. */
export function BalanceWatchProvider({
  shortcodeUuid,
  children,
}: {
  shortcodeUuid: string;
  children: ReactNode;
}) {
  const [waitingFor, setWaitingFor] = useState<string | null>(null);
  const status = useGetShortcodeBalance(shortcodeUuid, {
    query: { refetchInterval: waitingFor ? POLL_MS : false },
  });
  const data = okData(status.data);
  const invalidate = useInvalidateShortcodes();

  useEffect(() => {
    if (!waitingFor || !data || !querySettled(data.last_query, waitingFor)) return;
    const query = data.last_query;
    setWaitingFor(null);
    if (query?.status === "COMPLETED") toast.success("Safaricom reported the balances");
    else
      toast.error(
        query?.status === "TIMED_OUT" ? "Safaricom timed out" : "The balance query failed",
        { description: query?.result_desc ?? undefined },
      );
    void invalidate();
  }, [waitingFor, data, invalidate]);

  useEffect(() => {
    if (!waitingFor) return;
    const timer = setTimeout(() => {
      setWaitingFor(null);
      toast.info("Safaricom hasn't answered yet", {
        description: "The balances update here when the result arrives.",
      });
    }, GIVE_UP_MS);
    return () => clearTimeout(timer);
  }, [waitingFor]);

  const value = useMemo(
    () => ({ status, data, waiting: waitingFor !== null, watch: setWaitingFor }),
    [status, data, waitingFor],
  );
  return <BalanceWatchContext.Provider value={value}>{children}</BalanceWatchContext.Provider>;
}

export function useBalanceWatch(): BalanceWatch {
  const watch = useContext(BalanceWatchContext);
  if (!watch) throw new Error("useBalanceWatch needs a BalanceWatchProvider above it");
  return watch;
}
