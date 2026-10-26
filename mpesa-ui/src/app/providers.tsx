"use client";

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SessionProvider } from "next-auth/react";
import { ThemeProvider } from "next-themes";
import { type ReactNode, useState } from "react";
import { toast } from "sonner";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { type DescribeOptions, describeApiError, isClientError } from "@/lib/api-errors";

function toastError(error: unknown, options?: DescribeOptions) {
  const copy = describeApiError(error, options);
  // A 401 already sends the browser to sign-in.
  if (copy.kind === "unauthorized") return;
  toast.error(copy.title, { description: copy.description, id: `${copy.kind}:${copy.title}` });
}

function makeQueryClient() {
  return new QueryClient({
    // Queries render their own ErrorState or StaleNotice; only opted-in ones toast, and never over data.
    queryCache: new QueryCache({
      onError: (error, query) => {
        if (query.meta?.errorToast && query.state.data === undefined) toastError(error);
      },
    }),
    mutationCache: new MutationCache({
      onError: (error, _variables, _context, mutation) => {
        if (mutation.meta?.errorToast === false) return;
        toastError(error, { requiredRole: mutation.meta?.requiredRole });
      },
    }),
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: true,
        // A 4xx will not fix itself; network trouble and 5xx get two more tries with backoff.
        retry: (failureCount, error) => !isClientError(error) && failureCount < 2,
        retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 8000),
      },
      mutations: { retry: false },
    },
  });
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(makeQueryClient);

  return (
    // Polling the session lets the server refresh Keycloak tokens before they expire.
    <SessionProvider refetchInterval={60} refetchOnWindowFocus>
      <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
        <QueryClientProvider client={queryClient}>
          <TooltipProvider>
            {children}
            <Toaster richColors />
          </TooltipProvider>
        </QueryClientProvider>
      </ThemeProvider>
    </SessionProvider>
  );
}
