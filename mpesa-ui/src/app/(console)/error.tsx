"use client";

import Link from "next/link";
import { ErrorState } from "@/components/error-state";
import { SectionCard } from "@/components/section-card";
import { Button } from "@/components/ui/button";

// The sidebar and header stay; only the page area is replaced.
export default function ConsoleError({ error, retry }: { error: unknown; retry: () => void }) {
  return (
    <SectionCard className="mx-auto w-full max-w-xl">
      <ErrorState
        error={error}
        title="This page didn't load"
        description="Something went wrong while opening it. Try again, or head back to the overview."
        onRetry={retry}
        action={
          <Button variant="ghost" size="sm" asChild>
            <Link href="/">Go to overview</Link>
          </Button>
        }
      />
    </SectionCard>
  );
}
