import type { ShortcodeDetail } from "@/api/generated/model";

/** What every paybill detail tab gets: the shortcode as the page loaded it (refetched after any change). */
export type PaybillSectionProps = { shortcode: ShortcodeDetail };
