"use client";

import { Landmark, Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import {
  useDeleteB2bDestination,
  useListB2bDestinations,
} from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { okData, toastApiError, useInvalidateShortcodes } from "@/components/paybills/api";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { TableSkeleton } from "@/components/skeletons";
import { SortableHead } from "@/components/sortable-head";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { cn } from "@/lib/utils";
import { DestinationDialog } from "./destination-dialog";

/** The paybills and tills this shortcode may sweep to; admins add, edit and remove them. */
export function B2bDestinations({ shortcodeUuid }: { shortcodeUuid: string }) {
  const paging = usePaging({ prefix: "destinations", size: 10 });
  const list = useListB2bDestinations(
    shortcodeUuid,
    { page: paging.page, size: paging.size, sort: paging.sort ? [paging.sort] : undefined },
    { query: pagedQueryOptions },
  );
  const data = okData(list.data);
  useClampPage(data, paging.page, paging.setPage);
  const invalidate = useInvalidateShortcodes();
  const remove = useDeleteB2bDestination({
    mutation: { meta: { errorToast: false }, onError: (error) => toastApiError(error) },
  });

  return (
    <SectionCard
      title="B2B destinations"
      description="Where sweeps from this shortcode may go. Only active ones can be picked."
      actions={
        <RoleGate needs="mpesa-admin">
          <DestinationDialog
            shortcodeUuid={shortcodeUuid}
            trigger={
              <Button variant="outline" size="sm">
                <Plus /> Add destination
              </Button>
            }
          />
        </RoleGate>
      }
    >
      {data && <StaleNotice query={list} />}
      {list.isPending ? (
        <TableSkeleton columns={5} rows={2} />
      ) : !data ? (
        <ErrorState error={list.error} onRetry={() => list.refetch()} retrying={list.isFetching} />
      ) : data.items.length === 0 ? (
        <EmptyState
          icon={Landmark}
          title="No destinations yet"
          description="An admin adds the bank paybill or till that sweeps should go to."
        />
      ) : (
        <>
          <div
            className={cn(
              "overflow-x-auto rounded-lg border transition-opacity",
              list.isPlaceholderData && "opacity-60",
            )}
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <SortableHead
                    field="name"
                    sort={paging.sort}
                    defaultField="name"
                    onSort={paging.setSort}
                  >
                    Name
                  </SortableHead>
                  <TableHead>Receiver</TableHead>
                  <TableHead>Account</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((destination) => (
                  <TableRow key={destination.uuid}>
                    <TableCell className="font-medium">{destination.name}</TableCell>
                    <TableCell>
                      <span className="font-mono tabular-nums">
                        {destination.receiver_shortcode}
                      </span>
                      <span className="text-muted-foreground">
                        {" "}
                        · {destination.receiver_type === "TILL" ? "Till" : "Paybill"}
                      </span>
                    </TableCell>
                    <TableCell className="font-mono">
                      {destination.account_reference ?? "—"}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={destination.active ? "ACTIVE" : "INACTIVE"} />
                    </TableCell>
                    <TableCell className="text-right">
                      <RoleGate needs="mpesa-admin">
                        <div className="flex justify-end gap-1">
                          <DestinationDialog
                            shortcodeUuid={shortcodeUuid}
                            destination={destination}
                            trigger={
                              <Button
                                variant="ghost"
                                size="icon-sm"
                                aria-label={`Edit ${destination.name}`}
                              >
                                <Pencil />
                              </Button>
                            }
                          />
                          <ConfirmDialog
                            title={`Remove ${destination.name}?`}
                            description="Sweeps can no longer go there. Transfers already sent keep their record."
                            confirmLabel="Remove"
                            destructive
                            pending={remove.isPending}
                            trigger={
                              <Button
                                variant="ghost"
                                size="icon-sm"
                                aria-label={`Remove ${destination.name}`}
                              >
                                <Trash2 />
                              </Button>
                            }
                            onConfirm={async () => {
                              await remove.mutateAsync({
                                uuid: shortcodeUuid,
                                destinationUuid: destination.uuid,
                              });
                              toast.success(`${destination.name} removed`);
                              await invalidate();
                            }}
                          />
                        </div>
                      </RoleGate>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <DataTablePagination
            meta={data}
            onPageChange={paging.setPage}
            onPageSizeChange={paging.setSize}
            disabled={list.isPlaceholderData}
          />
        </>
      )}
    </SectionCard>
  );
}
