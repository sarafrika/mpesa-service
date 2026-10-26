import type { NextRequest } from "next/server";
import { getToken } from "next-auth/jwt";
import { UNREACHABLE_HEADER } from "@/lib/service-status";

const SERVICE_URL = process.env.MPESA_SERVICE_URL ?? "http://localhost:8080";
// Only the machine and admin APIs are reachable; Safaricom callbacks never go through the console.
const ALLOWED = [/^api\/v1\/mpesa\//, /^api\/v1\/admin\//];
const FORWARDED_HEADERS = ["accept", "content-type", "idempotency-key"];

// Same shape as the service's own ApiError body, so the browser parses one format.
function apiError(status: number, error: string, message: string, headers?: HeadersInit) {
  return Response.json({ status, error, message, field_errors: [] }, { status, headers });
}

async function forward(request: NextRequest, ctx: RouteContext<"/api/mpesa/[...path]">) {
  const { path } = await ctx.params;
  const target = path.join("/");
  if (!ALLOWED.some((rule) => rule.test(`${target}/`))) {
    return apiError(404, "Not Found", "Not proxied");
  }

  const token = await getToken({ req: request });
  if (!token?.accessToken || token.error) {
    return apiError(401, "Unauthorized", "Sign in again");
  }

  const headers = new Headers({ authorization: `Bearer ${token.accessToken}` });
  for (const name of FORWARDED_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }

  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  let upstream: Response;
  try {
    upstream = await fetch(`${SERVICE_URL}/${target}${request.nextUrl.search}`, {
      method: request.method,
      headers,
      body: hasBody ? await request.arrayBuffer() : undefined,
      cache: "no-store",
    });
  } catch {
    return apiError(503, "Service Unavailable", "Can't reach the M-Pesa service", {
      [UNREACHABLE_HEADER]: "1",
    });
  }

  return new Response(upstream.body, {
    status: upstream.status,
    headers: { "content-type": upstream.headers.get("content-type") ?? "application/json" },
  });
}

export { forward as GET, forward as POST, forward as PUT, forward as PATCH, forward as DELETE };
