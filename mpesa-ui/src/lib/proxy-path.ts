// Only the machine and admin APIs are reachable; Safaricom callbacks never go through the console.
const ALLOWED = [/^\/api\/v1\/mpesa\/./, /^\/api\/v1\/admin\/./];

const unsafe = (s: string) => s === "" || s === "." || s === ".." || /[/\\]/.test(s);

// Decodes until nothing changes, so a double-encoded "%252e%252e" cannot become ".." further down.
function safeSegment(segment: string): boolean {
  let current = segment;
  for (let i = 0; i < 5; i++) {
    if (unsafe(current)) return false;
    let decoded: string;
    try {
      decoded = decodeURIComponent(current);
    } catch {
      return false;
    }
    if (decoded === current) return true;
    current = decoded;
  }
  return false;
}

/** The mpesa-service URL for a proxied path, or null when the path is not one the console forwards. */
export function proxyTarget(segments: string[], search: string, serviceUrl: string): URL | null {
  if (segments.length === 0 || !segments.every(safeSegment)) return null;
  const base = new URL(serviceUrl);
  const basePath = base.pathname.replace(/\/+$/, "");
  const path = `${basePath}/${segments.map(encodeURIComponent).join("/")}`;
  const target = new URL(path, base.origin);
  if (target.origin !== base.origin || !target.pathname.startsWith(`${basePath}/`)) return null;
  if (!ALLOWED.some((rule) => rule.test(target.pathname.slice(basePath.length)))) return null;
  target.search = search;
  return target;
}
