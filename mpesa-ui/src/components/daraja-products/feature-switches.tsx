"use client";

import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  getListShortcodeFeaturesQueryKey,
  useListShortcodeFeatures,
  useUpdateShortcodeFeature,
} from "@/api/generated/endpoints/admin-features/admin-features";
import type { ShortcodeFeatureDto } from "@/api/generated/model";
import { ErrorState } from "@/components/error-state";
import { okData } from "@/components/paybills/api";
import { SectionCard } from "@/components/section-card";
import { FormSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { Switch } from "@/components/ui/switch";
import { useHasRole } from "@/hooks/use-has-role";
import { formatDateTime } from "@/lib/format";
import { canToggle, FEATURE_INFO, type Feature, featureNote, isFeature } from "./feature-catalog";

/** One switch per flagged Daraja product; only admins flip them, and unconfirmed products stay off. */
export function FeatureSwitches({ shortcodeUuid }: { shortcodeUuid: string }) {
  const list = useListShortcodeFeatures(shortcodeUuid);
  const data = okData(list.data);
  const isAdmin = useHasRole("mpesa-admin");

  return (
    <SectionCard
      title="Features"
      description="Daraja products Safaricom enables separately on each app. Switch one on only after Safaricom has enabled it for this shortcode."
    >
      {data && <StaleNotice query={list} />}
      {list.isPending ? (
        <FormSkeleton fields={5} />
      ) : !data ? (
        <ErrorState error={list.error} onRetry={() => list.refetch()} retrying={list.isFetching} />
      ) : (
        <ul className="divide-y rounded-lg border">
          {data
            .filter((row) => isFeature(row.feature))
            .map((row) => (
              <FeatureRow
                key={row.feature}
                shortcodeUuid={shortcodeUuid}
                row={row}
                feature={row.feature as Feature}
                isAdmin={isAdmin}
              />
            ))}
        </ul>
      )}
      {!isAdmin && (
        <p className="text-muted-foreground text-sm">Only an admin can switch features.</p>
      )}
    </SectionCard>
  );
}

type FeatureRowProps = {
  shortcodeUuid: string;
  row: ShortcodeFeatureDto;
  feature: Feature;
  isAdmin: boolean;
};

function FeatureRow({ shortcodeUuid, row, feature, isAdmin }: FeatureRowProps) {
  const queryClient = useQueryClient();
  const update = useUpdateShortcodeFeature({
    mutation: {
      onSuccess: (_, variables) => {
        queryClient.invalidateQueries({
          queryKey: getListShortcodeFeaturesQueryKey(shortcodeUuid),
        });
        toast.success(
          `${FEATURE_INFO[feature].label} switched ${variables.data.enabled ? "on" : "off"}`,
        );
      },
    },
  });
  const info = FEATURE_INFO[feature];
  const note = featureNote(feature);
  const id = `feature-${feature}`;
  const enabled = info.available && row.enabled;

  return (
    <li className="flex items-start justify-between gap-4 p-4">
      <div className="flex min-w-0 flex-col gap-1">
        <label htmlFor={id} className="font-medium">
          {info.label}
        </label>
        <p className="text-muted-foreground text-sm">{info.description}</p>
        {note && (
          <p className={info.available ? "text-sm text-warning" : "text-muted-foreground text-sm"}>
            {note}
          </p>
        )}
        {row.updated_by && (
          <p className="text-muted-foreground text-xs">
            Switched {row.enabled ? "on" : "off"} by {row.updated_by}
            {row.updated_at ? ` · ${formatDateTime(row.updated_at)}` : ""}
          </p>
        )}
      </div>
      <Switch
        id={id}
        checked={enabled}
        disabled={!canToggle(feature, isAdmin) || update.isPending}
        aria-label={`${info.label} ${enabled ? "on" : "off"}`}
        onCheckedChange={(checked) =>
          update.mutate({ uuid: shortcodeUuid, feature, data: { enabled: checked } })
        }
      />
    </li>
  );
}
