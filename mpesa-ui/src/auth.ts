import "server-only";
import type { NextAuthOptions } from "next-auth";
import type { JWT } from "next-auth/jwt";
import KeycloakProvider from "next-auth/providers/keycloak";

const issuer = process.env.KEYCLOAK_ISSUER ?? "";
// Server-to-Keycloak calls use this when the browser's issuer URL is not reachable from here (docker).
const backchannel = process.env.KEYCLOAK_INTERNAL_ISSUER || issuer;
const clientId = process.env.KEYCLOAK_CLIENT_ID ?? "mpesa-ui";

/** Realm roles granted to the user, read from the access token Keycloak issued. */
function realmRoles(accessToken: string): string[] {
  const payload = JSON.parse(Buffer.from(accessToken.split(".")[1], "base64url").toString());
  return payload?.realm_access?.roles ?? [];
}

async function refresh(token: JWT): Promise<JWT> {
  try {
    const response = await fetch(`${backchannel}/protocol/openid-connect/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        client_id: clientId,
        refresh_token: token.refreshToken ?? "",
      }),
    });
    const tokens = await response.json();
    if (!response.ok) throw new Error(tokens.error ?? "refresh failed");
    return {
      ...token,
      accessToken: tokens.access_token,
      refreshToken: tokens.refresh_token ?? token.refreshToken,
      expiresAt: Math.floor(Date.now() / 1000) + tokens.expires_in,
      roles: realmRoles(tokens.access_token),
      error: undefined,
    };
  } catch {
    return { ...token, error: "RefreshFailed" };
  }
}

export const authOptions: NextAuthOptions = {
  providers: [
    KeycloakProvider({
      clientId,
      // Public client: PKCE proves the code exchange instead of a client secret.
      clientSecret: "",
      client: { token_endpoint_auth_method: "none" },
      checks: ["pkce", "state"],
      issuer,
      wellKnown: `${backchannel}/.well-known/openid-configuration`,
    }),
  ],
  session: { strategy: "jwt" },
  pages: { signIn: "/sign-in" },
  callbacks: {
    async jwt({ token, account }) {
      if (account?.access_token) {
        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          idToken: account.id_token,
          expiresAt: account.expires_at,
          roles: realmRoles(account.access_token),
        };
      }
      if (token.expiresAt && Date.now() / 1000 < token.expiresAt - 60) return token;
      return refresh(token);
    },
    // The browser sees who is signed in and their roles, never the tokens.
    async session({ session, token }) {
      session.roles = token.roles ?? [];
      session.error = token.error;
      return session;
    },
  },
};
