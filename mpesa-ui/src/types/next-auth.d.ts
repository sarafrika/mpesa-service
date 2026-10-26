import "next-auth";
import "next-auth/jwt";

declare module "next-auth" {
  interface Session {
    roles: string[];
    error?: "RefreshFailed";
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    accessToken?: string;
    refreshToken?: string;
    idToken?: string;
    expiresAt?: number;
    roles?: string[];
    error?: "RefreshFailed";
  }
}
