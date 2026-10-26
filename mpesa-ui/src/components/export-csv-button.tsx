"use client";

import { useMutation } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { downloadCsv } from "@/lib/download";
import { formatCount } from "@/lib/format";

type ExportCsvButtonProps = {
  label: string;
  /** API path with the current filters, from Orval's get…Url helper. */
  url: () => string;
  method: "GET" | "POST";
  fileName: string;
  requiredRole?: string;
};

/** Downloads the current filters as CSV; a failure is toasted by the global mutation handler. */
export function ExportCsvButton({
  label,
  url,
  method,
  fileName,
  requiredRole,
}: ExportCsvButtonProps) {
  const download = useMutation({
    mutationFn: () => downloadCsv(url(), method, fileName),
    meta: { requiredRole },
    onSuccess: ({ total, truncated }) => {
      if (truncated && total !== null) {
        toast.warning(`Exported the first rows of ${formatCount(total)}`, {
          description: "The export limit was reached; narrow the filters to get the rest.",
        });
      } else if (total === 0) {
        toast.info("Nothing matches these filters, so the file only has headings");
      }
    },
  });
  return (
    <Button
      variant="outline"
      onClick={() => {
        if (!download.isPending) download.mutate();
      }}
      disabled={download.isPending}
      aria-busy={download.isPending || undefined}
    >
      <Download /> {download.isPending ? "Exporting…" : label}
    </Button>
  );
}
