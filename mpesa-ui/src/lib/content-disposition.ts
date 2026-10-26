/** The file name a Content-Disposition header gives, or the fallback. */
export function fileNameFrom(disposition: string | null, fallback: string): string {
  const match = disposition?.match(/filename="?([^";]+)"?/i);
  return match?.[1]?.trim() || fallback;
}
