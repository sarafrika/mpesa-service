"use client";

import { Search } from "lucide-react";
import { type FormEvent, useState } from "react";
import { useGetPayments } from "@/api/generated/endpoints/m-pesa-payments/m-pesa-payments";
import type { GetPaymentsParams } from "@/api/generated/model";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

const kes = new Intl.NumberFormat("en-KE", {
  style: "currency",
  currency: "KES",
  currencyDisplay: "code",
});
const when = new Intl.DateTimeFormat("en-KE", { dateStyle: "medium", timeStyle: "short" });

// M-Pesa receipts are 10 upper-case letters and digits; anything else is an account reference.
function toParams(query: string): GetPaymentsParams {
  return /^[A-Z0-9]{10}$/.test(query) ? { transaction_id: query } : { account_reference: query };
}

export function PaymentLookup() {
  const [draft, setDraft] = useState("");
  const [query, setQuery] = useState("");
  const payments = useGetPayments(toParams(query), { query: { enabled: query.length > 0 } });
  const rows = payments.data?.data ?? [];

  function submit(event: FormEvent) {
    event.preventDefault();
    setQuery(draft.trim());
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Find a payment</CardTitle>
        <CardDescription>
          Search incoming STK and C2B payments by account reference or M-Pesa receipt.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
          <div className="flex min-w-60 flex-1 flex-col gap-2">
            <Label htmlFor="payment-query">Account reference or receipt</Label>
            <Input
              id="payment-query"
              placeholder="ELM-004512 or TJQ4K2L9XA"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
            />
          </div>
          <Button type="submit" disabled={!draft.trim()}>
            <Search /> Search
          </Button>
        </form>

        {payments.isFetching && <Skeleton className="h-24 w-full" />}
        {payments.isError && (
          <p className="text-destructive text-sm">
            The lookup failed. Check the service is running and try again.
          </p>
        )}
        {payments.isSuccess && rows.length === 0 && (
          <p className="text-muted-foreground text-sm">No payments match “{query}”.</p>
        )}
        {rows.length > 0 && (
          <div className="overflow-x-auto rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Receipt</TableHead>
                  <TableHead>Account</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((payment) => (
                  <TableRow key={payment.uuid}>
                    <TableCell className="text-muted-foreground">
                      {payment.created_at ? when.format(new Date(payment.created_at)) : "—"}
                    </TableCell>
                    <TableCell>
                      {payment.payment_type === "C2B" ? "C2B deposit" : "STK push"}
                    </TableCell>
                    <TableCell className="font-mono text-xs">
                      {payment.transaction_id ?? "—"}
                    </TableCell>
                    <TableCell>{payment.account_reference ?? "—"}</TableCell>
                    <TableCell className="text-right font-medium tabular-nums">
                      {payment.amount != null ? kes.format(payment.amount) : "—"}
                    </TableCell>
                    <TableCell>
                      {payment.status && <StatusBadge status={payment.status} />}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
