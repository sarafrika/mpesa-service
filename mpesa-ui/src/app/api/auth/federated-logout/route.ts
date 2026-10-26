import type { NextRequest } from "next/server";
import { getToken } from "next-auth/jwt";

// Keycloak's end-session URL for this user; the browser goes there after next-auth signs out.
export async function POST(request: NextRequest) {
  const appUrl = process.env.NEXTAUTH_URL ?? request.nextUrl.origin;
  const signIn = `${appUrl.replace(/\/$/, "")}/sign-in`;
  const issuer = process.env.KEYCLOAK_ISSUER;
  if (!issuer) return Response.json({ url: signIn });

  const token = await getToken({ req: request });
  const params = new URLSearchParams({
    post_logout_redirect_uri: signIn,
    client_id: process.env.KEYCLOAK_CLIENT_ID ?? "mpesa-ui",
  });
  if (token?.idToken) params.set("id_token_hint", token.idToken);

  return Response.json(
    { url: `${issuer}/protocol/openid-connect/logout?${params}` },
    { headers: { "cache-control": "no-store" } },
  );
}
