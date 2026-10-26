"use client";

import Link from "next/link";
import { useState } from "react";
import type { AuditEventDto } from "@/api/generated/model";
import { KeyValueList } from "@/components/key-value-list";
import { DetailPanelSkeleton } from "@/components/skeletons";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Switch } from "@/components/ui/switch";
import { useHasRole } from "@/hooks/use-has-role";
import { formatDateTime } from "@/lib/format";
import { redactPersonalData } from "@/lib/redact";
import { cn } from "@/lib/utils";
import { type DiffChange, diffRecords } from "./audit-diff";

type AuditDetailSheetProps = {
  /** The open entry, from the current page; undefined while loading or when it is not on it. */
  event?: AuditEventDto;
  open: boolean;
  loading: boolean;
  onClose: () => void;
};

const CHANGE_LABEL: Record<DiffChange, string> = {
  added: "Added",
  removed: "Removed",
  changed: "Changed",
  same: "Unchanged",
};

const CHANGE_CLASS: Record<DiffChange, string> = {
  added: "bg-success-soft",
  removed: "bg-destructive-soft",
  changed: "bg-warning-soft",
  same: "",
};

/** Side panel for one audit entry: who, when and a field-by-field before/after (secrets redacted). */
export function AuditDetailSheet({ event, open, loading, onClose }: AuditDetailSheetProps) {
  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="w-full gap-0 overflow-y-auto data-[side=right]:sm:max-w-xl">
        {event ? (
          <DetailContent event={event} />
        ) : (
          <>
            <SheetHeader>
              <SheetTitle>Audit event</SheetTitle>
              <SheetDescription>
                {loading
                  ? "Loading the entry"
                  : "This entry is not on the page you are looking at."}
              </SheetDescription>
            </SheetHeader>
            <div className="px-4 pb-6">
              {loading ? (
                <DetailPanelSkeleton rows={6} />
              ) : (
                <Button variant="outline" onClick={onClose}>
                  Close
                </Button>
              )}
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}

function DetailContent({ event }: { event: AuditEventDto }) {
  // The service redacts secrets; personal data is hidden again here for anyone below finance.
  const canSeePersonal = useHasRole("mpesa-finance");
  const before = canSeePersonal ? event.before : redactPersonalData(event.before);
  const after = canSeePersonal ? event.after : redactPersonalData(event.after);
  const rows = diffRecords(before, after);
  const changed = rows.filter((row) => row.change !== "same");
  const [showAll, setShowAll] = useState(changed.length === 0);
  const shown = showAll ? rows : changed;
  const entityLink =
    event.entity_type === "SHORTCODE" && event.entity_id
      ? `/paybills/${event.entity_id}`
      : undefined;

  return (
    <>
      <SheetHeader>
        <SheetTitle className="font-mono text-base">{event.action}</SheetTitle>
        <SheetDescription>
          {event.actor_name ?? event.actor} · {formatDateTime(event.created_at)}
        </SheetDescription>
      </SheetHeader>
      <div className="flex flex-col gap-6 px-4 pb-6">
        <KeyValueList
          items={[
            { label: "Actor", value: event.actor, mono: true },
            { label: "Entity type", value: event.entity_type, mono: true },
            {
              label: "Entity id",
              value: entityLink ? (
                <Link href={entityLink} className="text-link underline-offset-4 hover:underline">
                  {event.entity_id}
                </Link>
              ) : (
                event.entity_id
              ),
              mono: true,
            },
            { label: "IP", value: event.ip, mono: true },
          ]}
        />

        <section className="flex flex-col gap-3" aria-labelledby="audit-diff-title">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 id="audit-diff-title" className="font-medium text-sm">
              {changed.length === 0
                ? "No field changed"
                : `${changed.length} field${changed.length === 1 ? "" : "s"} changed`}
            </h3>
            {rows.length > changed.length && changed.length > 0 && (
              <div className="flex items-center gap-2">
                <Switch id="audit-show-all" checked={showAll} onCheckedChange={setShowAll} />
                <Label htmlFor="audit-show-all" className="text-muted-foreground text-xs">
                  Show unchanged fields
                </Label>
              </div>
            )}
          </div>
          {shown.length === 0 ? (
            <p className="text-muted-foreground text-sm">
              This entry has no before or after record.
            </p>
          ) : (
            <div className="overflow-x-auto rounded-lg border">
              <table className="w-full text-left text-[13px]">
                <thead className="bg-muted/50 text-muted-foreground text-xs">
                  <tr>
                    <th className="px-3 py-2 font-medium">Field</th>
                    <th className="px-3 py-2 font-medium">Before</th>
                    <th className="px-3 py-2 font-medium">After</th>
                  </tr>
                </thead>
                <tbody>
                  {shown.map((row) => (
                    <tr
                      key={row.path}
                      className={cn("border-t align-top", CHANGE_CLASS[row.change])}
                    >
                      <th scope="row" className="px-3 py-2 font-mono font-normal">
                        {row.path}
                        <span className="sr-only"> ({CHANGE_LABEL[row.change]})</span>
                      </th>
                      <td className="break-all px-3 py-2 font-mono">{row.before ?? "—"}</td>
                      <td className="break-all px-3 py-2 font-mono">{row.after ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="text-muted-foreground text-xs">
            Secrets are stored as [REDACTED]; they never reach the audit trail.
          </p>
        </section>
      </div>
    </>
  );
}
