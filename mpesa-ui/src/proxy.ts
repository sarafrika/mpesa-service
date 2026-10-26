import { type NextRequest, NextResponse } from "next/server";
import { getToken } from "next-auth/jwt";

// Every console page needs a Keycloak session; the API routes check their own.
export async function proxy(request: NextRequest) {
  const token = await getToken({ req: request });
  if (token && !token.error) return NextResponse.next();

  const signIn = new URL("/sign-in", request.url);
  signIn.searchParams.set("callbackUrl", request.nextUrl.href);
  return NextResponse.redirect(signIn);
}

export const config = {
  matcher: ["/((?!api|sign-in|_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|ico)$).*)"],
};
