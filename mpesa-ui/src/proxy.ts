import { type NextRequest, NextResponse } from "next/server";
import { getToken } from "next-auth/jwt";

// Every console page needs a Keycloak session; the API routes check their own.
export async function proxy(request: NextRequest) {
  const token = await getToken({ req: request });
  if (token && !token.error) return NextResponse.next();

  // Behind a proxy request.url is the container's own address (0.0.0.0:3000), so use the public one.
  const origin = process.env.NEXTAUTH_URL ?? request.nextUrl.origin;
  const here = request.nextUrl.pathname + request.nextUrl.search;
  const signIn = new URL("/sign-in", origin);
  signIn.searchParams.set("callbackUrl", new URL(here, origin).href);
  return NextResponse.redirect(signIn);
}

export const config = {
  matcher: ["/((?!api|sign-in|_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|ico)$).*)"],
};
