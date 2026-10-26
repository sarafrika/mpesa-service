import { SearchX } from "lucide-react";
import Link from "next/link";
import { EmptyState } from "@/components/empty-state";
import { SectionCard } from "@/components/section-card";
import { Button } from "@/components/ui/button";

/** Shared body of both not-found pages. */
export function NotFoundCard({ description }: { description?: string }) {
  return (
    <SectionCard className="mx-auto w-full max-w-xl">
      <EmptyState
        icon={SearchX}
        title="We couldn't find that page"
        description={description ?? "The link may be old, or the page may have moved."}
        action={
          <Button size="sm" asChild>
            <Link href="/">Go to overview</Link>
          </Button>
        }
      />
    </SectionCard>
  );
}
