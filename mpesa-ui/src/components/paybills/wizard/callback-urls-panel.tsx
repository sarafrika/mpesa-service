"use client";

import { useGetShortcodeCallbackUrls } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import { ErrorState } from "@/components/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { okData } from "../api";

/** The URLs this service registers with Safaricom; each paybill's own token fills {callback-token}. */
export function CallbackUrlsPanel({ kind }: { kind: string }) {
  const query = useGetShortcodeCallbackUrls({ query: { staleTime: 5 * 60_000 } });
  const urls = okData(query.data);

  return (
    <div className="flex flex-col gap-1.5 rounded-lg border bg-muted/40 p-3.5">
      <span className="font-medium text-sm">Callback URLs are generated for you</span>
      <p className="text-muted-foreground text-xs">
        Each paybill gets its own secret token in place of {"{callback-token}"} once it is saved, so
        callbacks to a guessed URL are refused.
      </p>
      {query.isPending ? (
        <div className="flex flex-col gap-2" aria-busy="true">
          <Skeleton className="h-4 w-full max-w-md" />
          <Skeleton className="h-4 w-full max-w-sm" />
        </div>
      ) : !urls ? (
        <ErrorState
          className="py-4"
          error={query.error}
          onRetry={() => query.refetch()}
          retrying={query.isFetching}
        />
      ) : (
        <dl className="grid gap-1 font-mono text-[13px] text-foreground/80">
          {(kind === "B2C"
            ? [
                ["B2C result", urls.b2c_result_url],
                ["B2C timeout", urls.b2c_timeout_url],
              ]
            : [
                ["C2B confirmation", urls.c2b_confirmation_url],
                ["C2B validation", urls.c2b_validation_url],
                ["STK callback", urls.stk_callback_url],
              ]
          ).map(([label, url]) => (
            <div key={label} className="flex flex-wrap gap-x-2">
              <dt className="font-sans text-muted-foreground">{label}</dt>
              <dd className="min-w-0 break-all">{url}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}
