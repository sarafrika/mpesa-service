"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { type ComponentType, useState } from "react";
import { useGetShortcode } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import type { ShortcodeDetail } from "@/api/generated/model";
import { SectionBoundary } from "@/components/error-boundary";
import { ErrorState } from "@/components/error-state";
import { PageHeader } from "@/components/page-header";
import { QrCodeDialog } from "@/components/qr/qr-code-dialog";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { DetailPanelSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { BalanceBand, BandError } from "@/components/treasury/balance-band";
import { BalanceWatchProvider } from "@/components/treasury/balance-watch";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { isApiError } from "@/lib/api-errors";
import { cn } from "@/lib/utils";
import { okData } from "./api";
import { can, type TabRule, visibleTabs } from "./capabilities";
import { LifecycleButton, SendTestStkDialog } from "./lifecycle-actions";
import { EnvironmentBadge, PaybillStatusBadge } from "./paybill-badges";
import { initials } from "./paybill-health";
import type { PaybillSectionProps } from "./sections/types";
import { typeLabel } from "./shortcode-types";

/** Structural copy of PaybillSection so this component doesn't import the route file. */
type Section = TabRule & {
  label: string;
  Component: ComponentType<PaybillSectionProps>;
};

export function PaybillDetail({ uuid, sections }: { uuid: string; sections: readonly Section[] }) {
  const query = useGetShortcode(uuid);
  const shortcode = okData(query.data);

  if (query.isPending) {
    return (
      <SectionCard>
        <DetailPanelSkeleton rows={6} />
      </SectionCard>
    );
  }
  if (!shortcode) {
    const missing = isApiError(query.error) && query.error.status === 404;
    return (
      <SectionCard className="mx-auto w-full max-w-xl">
        <ErrorState
          error={query.error}
          resource="shortcode"
          onRetry={missing ? undefined : () => query.refetch()}
          retrying={query.isFetching}
          action={
            <Button variant="ghost" size="sm" asChild>
              <Link href="/paybills">Back to paybills & tills</Link>
            </Button>
          }
        />
      </SectionCard>
    );
  }
  const floatTab = visibleTabs(sections, shortcode).some((section) => section.id === "float");
  return (
    <BalanceWatchProvider shortcodeUuid={shortcode.uuid}>
      <StaleNotice query={query} />
      <DetailHeader shortcode={shortcode} />
      <SectionBoundary fallback={({ error, reset }) => <BandError error={error} onRetry={reset} />}>
        <BalanceBand shortcode={shortcode} floatTab={floatTab} />
      </SectionBoundary>
      <DetailTabs shortcode={shortcode} sections={sections} />
    </BalanceWatchProvider>
  );
}

function DetailHeader({ shortcode }: { shortcode: ShortcodeDetail }) {
  const [now] = useState(() => Date.now());
  const stk = can(shortcode, "STK_PUSH");
  const canPush = shortcode.status === "ACTIVE" && Boolean(shortcode.credentials.passkey_masked);
  const till = shortcode.shortcode_type === "TILL";
  const onboarded = shortcode.created_at
    ? new Date(shortcode.created_at).toLocaleDateString("en-KE", { dateStyle: "medium" })
    : undefined;

  return (
    <PageHeader
      breadcrumbs={[
        { label: "Paybills & tills", href: "/paybills" },
        { label: shortcode.business_name },
      ]}
      title={
        <span className="flex items-center gap-3">
          <span
            aria-hidden
            className={cn(
              "flex size-12 shrink-0 items-center justify-center rounded-xl font-bold text-base",
              shortcode.status === "ACTIVE"
                ? "bg-success-soft text-success"
                : "bg-muted text-muted-foreground",
            )}
          >
            {initials(shortcode.business_name)}
          </span>
          <span className="min-w-0 break-words">{shortcode.business_name}</span>
        </span>
      }
      description={
        <span className="flex flex-wrap items-center gap-2">
          <PaybillStatusBadge row={shortcode} now={now} />
          <EnvironmentBadge environment={shortcode.environment} />
          <span>
            {typeLabel(shortcode.shortcode_type)} · {till && "Till "}
            <strong className="font-semibold text-foreground tabular-nums">
              {shortcode.shortcode}
            </strong>
            {till && shortcode.store_number && (
              <>
                {" · Store "}
                <strong className="font-semibold text-foreground tabular-nums">
                  {shortcode.store_number}
                </strong>
              </>
            )}
            {shortcode.products.length > 0 &&
              ` · Product: ${shortcode.products.map((product) => product.display_name ?? product.client_id).join(", ")}`}
            {onboarded && ` · Onboarded ${onboarded}`}
          </span>
        </span>
      }
      actions={
        <>
          {stk && (
            <RoleGate needs="mpesa-operator">
              <SendTestStkDialog shortcode={shortcode} disabled={!canPush} />
            </RoleGate>
          )}
          {can(shortcode, "QR") && (
            <RoleGate needs="mpesa-operator">
              <QrCodeDialog shortcode={shortcode} />
            </RoleGate>
          )}
          <RoleGate needs="mpesa-admin">
            <LifecycleButton shortcode={shortcode} />
          </RoleGate>
        </>
      }
    />
  );
}

function DetailTabs({
  shortcode,
  sections,
}: {
  shortcode: ShortcodeDetail;
  sections: readonly Section[];
}) {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const visible = visibleTabs(sections, shortcode);
  const requested = params.get("tab");
  const current = visible.find((section) => section.id === requested)?.id ?? visible[0]?.id;

  function select(tab: string) {
    const next = new URLSearchParams(params.toString());
    // Each tab keeps its own paging (plain or prefixed, e.g. transfers_page), so lists restart at the top.
    for (const key of [...next.keys()]) {
      if (/^(?:[a-z0-9]+_)?(?:page|size|sort)$/.test(key)) next.delete(key);
    }
    if (tab === visible[0]?.id) next.delete("tab");
    else next.set("tab", tab);
    const search = next.toString();
    router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
  }

  return (
    <Tabs value={current} onValueChange={select} className="gap-4">
      {/* pb-1 keeps the active underline inside the scroller, so no vertical scrollbar. */}
      <div className="overflow-x-auto border-b">
        <TabsList variant="line" className="pb-1" aria-label="Shortcode sections">
          {visible.map((section) => (
            <TabsTrigger key={section.id} value={section.id}>
              {section.label}
            </TabsTrigger>
          ))}
        </TabsList>
      </div>
      {visible.map((section) => (
        <TabsContent key={section.id} value={section.id}>
          <SectionBoundary title={section.label}>
            <section.Component shortcode={shortcode} />
          </SectionBoundary>
        </TabsContent>
      ))}
    </Tabs>
  );
}
