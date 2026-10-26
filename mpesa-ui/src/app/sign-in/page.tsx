import type { Metadata } from "next";
import Image from "next/image";
import { Suspense } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { SignInButton } from "./sign-in-button";

export const metadata: Metadata = { title: "Sign in" };

export default function SignInPage() {
  return (
    <main className="flex min-h-svh items-center justify-center bg-background p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="items-center gap-3 text-center">
          <Image src="/sarafrika-symbol.svg" alt="" width={48} height={48} priority />
          <CardTitle className="text-xl">Sarafrika M-Pesa console</CardTitle>
          <CardDescription>
            Sign in with your Sarafrika account to manage paybills and integrations.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Suspense>
            <SignInButton />
          </Suspense>
        </CardContent>
      </Card>
    </main>
  );
}
