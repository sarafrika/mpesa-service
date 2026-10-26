import type { Metadata } from "next";
import { NotFoundCard } from "@/components/not-found-card";

export const metadata: Metadata = { title: "Page not found" };

// Unmatched URLs land here, outside the console shell.
export default function NotFound() {
  return (
    <main className="flex min-h-svh items-center justify-center p-4">
      <NotFoundCard />
    </main>
  );
}
