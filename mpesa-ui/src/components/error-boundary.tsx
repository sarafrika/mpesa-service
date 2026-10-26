"use client";

import { QueryErrorResetBoundary } from "@tanstack/react-query";
import { Component, type ErrorInfo, type ReactNode } from "react";
import { ErrorState } from "@/components/error-state";
import { SectionCard } from "@/components/section-card";
import { isApiError } from "@/lib/api-errors";

export type ErrorFallbackProps = { error: unknown; reset: () => void };

type ErrorBoundaryProps = {
  children: ReactNode;
  fallback: (props: ErrorFallbackProps) => ReactNode;
  /** Runs before the children re-render, e.g. React Query's reset. */
  onReset?: () => void;
  onError?: (error: unknown, info: ErrorInfo) => void;
};

type ErrorBoundaryState = { error: unknown; failed: boolean };

/** Catches render errors below it and shows `fallback` instead of breaking the page. */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: undefined, failed: false };

  static getDerivedStateFromError(error: unknown): ErrorBoundaryState {
    return { error, failed: true };
  }

  componentDidCatch(error: unknown, info: ErrorInfo) {
    this.props.onError?.(error, info);
  }

  reset = () => {
    this.props.onReset?.();
    this.setState({ error: undefined, failed: false });
  };

  render() {
    if (this.state.failed)
      return this.props.fallback({ error: this.state.error, reset: this.reset });
    return this.props.children;
  }
}

type SectionBoundaryProps = {
  /** Card title shown on the fallback, so the user knows which part failed. */
  title?: ReactNode;
  children: ReactNode;
  className?: string;
  /** Replaces the default fallback card. */
  fallback?: (props: ErrorFallbackProps) => ReactNode;
};

/** Wrap every screen section in this: one failing card never takes the page down. */
export function SectionBoundary({ title, children, className, fallback }: SectionBoundaryProps) {
  return (
    <QueryErrorResetBoundary>
      {({ reset }) => (
        <ErrorBoundary
          onReset={reset}
          fallback={
            fallback ??
            (({ error, reset: retry }) => (
              <SectionCard title={title} className={className}>
                <ErrorState
                  error={error}
                  description={
                    isApiError(error)
                      ? undefined
                      : "This part of the page failed to load. The rest still works."
                  }
                  onRetry={retry}
                />
              </SectionCard>
            ))
          }
        >
          {children}
        </ErrorBoundary>
      )}
    </QueryErrorResetBoundary>
  );
}
