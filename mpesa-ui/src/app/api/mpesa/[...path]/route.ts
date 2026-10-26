import type { NextRequest } from "next/server";
import { getToken } from "next-auth/jwt";
import { proxyTarget } from "@/lib/proxy-path";
import { UNREACHABLE_HEADER } from "@/lib/service-status";

const SERVICE_URL = process.env.MPESA_SERVICE_URL ?? "http://localhost:8080";
const FORWARDED_HEADERS = ["accept", "content-type", "idempotency-key"];
// CSV exports name their file and say how many rows matched.
const RETURNED_HEADERS = ["content-disposition", "x-export-total", "x-export-truncated"];

// Same shape as the service's own ApiError body, so the browser parses one format.
function apiError(status: number, error: string, message: string, headers?: HeadersInit) {
  return Response.json({ status, error, message, field_errors: [] }, { status, headers });
}

async function forward(request: NextRequest, ctx: RouteContext<"/api/mpesa/[...path]">) {
  const { path } = await ctx.params;
  const target = proxyTarget(path, request.nextUrl.search, SERVICE_URL);
  if (!target) {
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
    upstream = await fetch(target, {
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

  const returned = new Headers({
    "content-type": upstream.headers.get("content-type") ?? "application/json",
  });
  for (const name of RETURNED_HEADERS) {
    const value = upstream.headers.get(name);
    if (value) returned.set(name, value);
  }
  return new Response(upstream.body, { status: upstream.status, headers: returned });
}

export { forward as GET, forward as POST, forward as PUT, forward as PATCH, forward as DELETE };
