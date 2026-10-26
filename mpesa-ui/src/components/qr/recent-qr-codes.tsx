"use client";

import { Download, QrCode } from "lucide-react";
import { useState } from "react";
import { PROXY_PREFIX } from "@/api/fetcher";
import { useListShortcodeQrCodes } from "@/api/generated/endpoints/admin-qr-codes/admin-qr-codes";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { Money } from "@/components/money";
import { LoadingRows } from "@/components/skeletons";
import { Button } from "@/components/ui/button";
import { pagedQueryOptions } from "@/hooks/use-paging";
import { formatDateTime } from "@/lib/format";

const PAGE_SIZE = 5;

/** The admin API path of a PNG as the browser fetches it, through the console's proxy. */
export function qrImageHref(imagePath: string): string {
  return `${PROXY_PREFIX}${imagePath}`;
}

/** QR codes generated for one paybill, newest first, each downloadable as PNG. */
export function RecentQrCodes({ shortcodeUuid }: { shortcodeUuid: string }) {
  // Paging stays local: this list lives in a dialog, not in the page URL.
  const [page, setPage] = useState(0);
  const query = useListShortcodeQrCodes(
    shortcodeUuid,
    { page, size: PAGE_SIZE, sort: ["created_at,desc"] },
    { query: pagedQueryOptions },
  );
  const data = query.data?.status === 200 ? query.data.data : undefined;

  if (!data) {
    return query.isError ? (
      <ErrorState
        error={query.error}
        requiredRole="mpesa-viewer"
        onRetry={() => query.refetch()}
        retrying={query.isFetching}
      />
    ) : (
      <LoadingRows rows={3} columns={3} />
    );
  }
  if (data.items.length === 0) {
    return (
      <EmptyState
        icon={QrCode}
        title="No QR codes yet"
        description="Generate one above; it is kept here so you can download it again."
      />
    );
  }
  return (
    <div className="flex flex-col gap-3">
      <ul
        className="flex flex-col divide-y rounded-lg border"
        aria-busy={query.isPlaceholderData || undefined}
      >
        {data.items.map((code) => (
          <li
            key={code.uuid}
            className="flex items-center justify-between gap-3 px-3 py-2.5 text-sm"
          >
            <div className="flex min-w-0 flex-col">
              <span className="truncate font-medium">
                {code.ref_no}
                <span className="text-muted-foreground"> · {code.trx_code}</span>
              </span>
              <span className="text-muted-foreground text-xs">
                {code.amount != null ? <Money amount={code.amount} /> : "No amount"} ·{" "}
                {formatDateTime(code.created_at)}
                {code.created_by ? ` · ${code.created_by}` : ""}
              </span>
            </div>
            <Button variant="ghost" size="icon" asChild>
              <a
                href={qrImageHref(code.image_path)}
                download={`qr-${code.cpi}-${code.ref_no}.png`}
                aria-label={`Download QR code ${code.ref_no}`}
              >
                <Download />
              </a>
            </Button>
          </li>
        ))}
      </ul>
      <DataTablePagination meta={data} onPageChange={setPage} disabled={query.isPlaceholderData} />
    </div>
  );
}
