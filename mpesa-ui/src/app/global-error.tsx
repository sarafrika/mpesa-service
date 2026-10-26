"use client";

import "./globals.css";
import { useEffect, useState } from "react";

// Replaces the root layout when it fails, so it brings its own document, styles and theme.
export default function GlobalError({ retry }: { error: unknown; retry: () => void }) {
  const [dark, setDark] = useState(false);
  useEffect(() => {
    setDark(
      document.documentElement.classList.contains("dark") ||
        window.matchMedia("(prefers-color-scheme: dark)").matches,
    );
  }, []);

  return (
    <html lang="en" className={dark ? "dark" : undefined}>
      <body className="flex min-h-svh items-center justify-center bg-background p-6 font-sans text-foreground antialiased">
        <title>Something went wrong · Sarafrika M-Pesa console</title>
        <main className="flex max-w-md flex-col items-center gap-4 rounded-xl border bg-card p-8 text-center">
          <h1 className="font-semibold text-xl tracking-tight">The console failed to load</h1>
          <p className="text-muted-foreground text-sm">
            Something broke before the page could open. Try again, and if it keeps happening, tell
            the platform team what you were doing.
          </p>
          <div className="flex flex-wrap justify-center gap-2">
            <button
              type="button"
              onClick={() => retry()}
              className="h-9 rounded-md bg-primary px-4 font-medium text-primary-foreground text-sm hover:bg-primary/80"
            >
              Try again
            </button>
            {/* A plain link: the router may be what failed. */}
            <a
              href="/"
              className="inline-flex h-9 items-center rounded-md border px-4 font-medium text-sm hover:bg-muted"
            >
              Go to overview
            </a>
          </div>
        </main>
      </body>
    </html>
  );
}
