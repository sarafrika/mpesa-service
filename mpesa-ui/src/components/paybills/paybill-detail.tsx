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
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { isApiError } from "@/lib/api-errors";
import { cn } from "@/lib/utils";
import { okData } from "./api";
import { LifecycleButton, SendTestStkDialog } from "./lifecycle-actions";
import { EnvironmentBadge, PaybillStatusBadge } from "./paybill-badges";
import { initials, TYPE_LABELS } from "./paybill-health";
import type { PaybillSectionProps } from "./sections/types";

/** Structural copy of PaybillSection so this component doesn't import the route file. */
type Section = {
  id: string;
  label: string;
  visible?: (shortcode: ShortcodeDetail) => boolean;
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
          resource="paybill"
          onRetry={missing ? undefined : () => query.refetch()}
          retrying={query.isFetching}
          action={
            <Button variant="ghost" size="sm" asChild>
              <Link href="/paybills">Back to paybills</Link>
            </Button>
          }
        />
      </SectionCard>
    );
  }
  return (
    <>
      <StaleNotice query={query} />
      <DetailHeader shortcode={shortcode} />
      <DetailTabs shortcode={shortcode} sections={sections} />
    </>
  );
}

function DetailHeader({ shortcode }: { shortcode: ShortcodeDetail }) {
  const [now] = useState(() => Date.now());
  const canPush =
    shortcode.status === "ACTIVE" &&
    shortcode.shortcode_type !== "B2C" &&
    Boolean(shortcode.credentials.passkey_masked);
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
            {TYPE_LABELS[shortcode.shortcode_type] ?? shortcode.shortcode_type}{" "}
            <strong className="font-semibold text-foreground tabular-nums">
              {shortcode.shortcode}
            </strong>
            {onboarded && ` · Onboarded ${onboarded}`}
          </span>
        </span>
      }
      actions={
        <>
          {shortcode.shortcode_type !== "B2C" && (
            <RoleGate needs="mpesa-operator">
              <SendTestStkDialog shortcode={shortcode} disabled={!canPush} />
            </RoleGate>
          )}
          {shortcode.shortcode_type !== "B2C" && (
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
  const visible = sections.filter((section) => section.visible?.(shortcode) ?? true);
  const requested = params.get("tab");
  const current = visible.find((section) => section.id === requested)?.id ?? visible[0]?.id;

  function select(tab: string) {
    const next = new URLSearchParams(params.toString());
    // Each tab keeps its own paging, so switching starts its lists from the top.
    for (const key of ["page", "size", "sort"]) next.delete(key);
    if (tab === visible[0]?.id) next.delete("tab");
    else next.set("tab", tab);
    const search = next.toString();
    router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
  }

  return (
    <Tabs value={current} onValueChange={select} className="gap-4">
      <div className="overflow-x-auto border-b">
        <TabsList variant="line" aria-label="Paybill sections">
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
