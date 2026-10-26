"use client";

import { CloudOff, Lock, RotateCw, SearchX, TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty";
import { type DescribeOptions, describeApiError, type ErrorKind } from "@/lib/api-errors";
import { cn } from "@/lib/utils";

const ICONS: Partial<Record<ErrorKind, typeof TriangleAlert>> = {
  offline: CloudOff,
  unavailable: CloudOff,
  forbidden: Lock,
  not_found: SearchX,
};

type ErrorStateProps = {
  /** The thrown error; its kind picks the copy and icon unless title/description are given. */
  error?: unknown;
  title?: ReactNode;
  description?: ReactNode;
  /** Shows "Try again" when the error is retryable (or always, if no error is passed). */
  onRetry?: () => void;
  retrying?: boolean;
  /** Extra actions beside "Try again", e.g. a link home. */
  action?: ReactNode;
  className?: string;
} & DescribeOptions;

export function ErrorState({
  error,
  title,
  description,
  onRetry,
  retrying = false,
  action,
  className,
  requiredRole,
  resource,
}: ErrorStateProps) {
  const copy = describeApiError(error, { requiredRole, resource });
  const Icon = ICONS[copy.kind] ?? TriangleAlert;
  const canRetry = onRetry && (error === undefined || copy.retryable);

  return (
    <Empty role="alert" className={cn("py-10", className)}>
      <EmptyHeader>
        <EmptyMedia variant="icon" className="bg-destructive-soft text-destructive">
          <Icon />
        </EmptyMedia>
        <EmptyTitle>{title ?? copy.title}</EmptyTitle>
        <EmptyDescription>{description ?? copy.description}</EmptyDescription>
      </EmptyHeader>
      {(canRetry || action) && (
        <EmptyContent className="flex-row flex-wrap justify-center">
          {canRetry && (
            <Button variant="outline" size="sm" onClick={onRetry} disabled={retrying}>
              <RotateCw className={cn(retrying && "animate-spin")} />
              {retrying ? "Retrying…" : "Try again"}
            </Button>
          )}
          {action}
        </EmptyContent>
      )}
    </Empty>
  );
}
