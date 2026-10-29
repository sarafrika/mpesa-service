"use client";

import { LogIn } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { signIn } from "next-auth/react";
import { Button } from "@/components/ui/button";

export function SignInButton() {
  const callbackUrl = useSearchParams().get("callbackUrl") ?? "/";
  return (
    <Button className="w-full" size="lg" onClick={() => signIn("keycloak", { callbackUrl })}>
      <LogIn />
      Continue with Sarafrika
    </Button>
  );
}
