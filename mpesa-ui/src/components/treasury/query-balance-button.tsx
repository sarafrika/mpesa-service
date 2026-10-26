"use client";

import { RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { useQueryShortcodeBalance } from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import { toastApiError } from "@/components/paybills/api";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { acceptedData } from "./api";

type QueryBalanceButtonProps = {
  shortcodeUuid: string;
  /** False when the shortcode has no initiator, which Account Balance needs. */
  enabled: boolean;
  /** True while the page waits for Safaricom's answer to an earlier click. */
  waiting: boolean;
  /** Called with the new query's uuid so the page can poll for its result. */
  onSent: (queryUuid: string) => void;
};

/** Sends an Account Balance query; Safaricom answers on the result URL a few seconds later. */
export function QueryBalanceButton({
  shortcodeUuid,
  enabled,
  waiting,
  onSent,
}: QueryBalanceButtonProps) {
  const query = useQueryShortcodeBalance({
    mutation: { meta: { errorToast: false }, onError: (error) => toastApiError(error) },
  });
  const busy = query.isPending || waiting;

  return (
    <Button
      variant="outline"
      size="sm"
      disabled={!enabled || busy}
      title={enabled ? undefined : "Add the shortcode's initiator under Credentials first"}
      onClick={async () => {
        const response = await query.mutateAsync({ uuid: shortcodeUuid }).catch(() => undefined);
        const pending = acceptedData(response);
        if (!pending) return;
        toast.info("Balance query sent", {
          description: "Safaricom answers in a few seconds; the balances update when it does.",
        });
        onSent(pending.uuid);
      }}
    >
      {busy ? <Spinner /> : <RefreshCw />}
      {waiting ? "Waiting for Safaricom…" : "Query balance now"}
    </Button>
  );
}
