import type { NextRequest } from "next/server";
import { getToken } from "next-auth/jwt";

const SERVICE_URL = process.env.MPESA_SERVICE_URL ?? "http://localhost:8080";
// Only the machine and admin APIs are reachable; Safaricom callbacks never go through the console.
const ALLOWED = [/^api\/v1\/mpesa\//, /^api\/v1\/admin\//];
const FORWARDED_HEADERS = ["accept", "content-type", "idempotency-key"];

async function forward(request: NextRequest, ctx: RouteContext<"/api/mpesa/[...path]">) {
  const { path } = await ctx.params;
  const target = path.join("/");
  if (!ALLOWED.some((rule) => rule.test(`${target}/`))) {
    return Response.json({ error: "Not proxied" }, { status: 404 });
  }

  const token = await getToken({ req: request });
  if (!token?.accessToken || token.error) {
    return Response.json({ error: "Sign in again" }, { status: 401 });
  }

  const headers = new Headers({ authorization: `Bearer ${token.accessToken}` });
  for (const name of FORWARDED_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }

  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  const upstream = await fetch(`${SERVICE_URL}/${target}${request.nextUrl.search}`, {
    method: request.method,
    headers,
    body: hasBody ? await request.arrayBuffer() : undefined,
    cache: "no-store",
  });

  return new Response(upstream.body, {
    status: upstream.status,
    headers: { "content-type": upstream.headers.get("content-type") ?? "application/json" },
  });
}

export { forward as GET, forward as POST, forward as PUT, forward as PATCH, forward as DELETE };
