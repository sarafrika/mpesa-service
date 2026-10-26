import "@tanstack/react-query";

declare module "@tanstack/react-query" {
  interface Register {
    queryMeta: {
      /** Toast when this query fails with no data to show (for queries without an ErrorState). */
      errorToast?: boolean;
    };
    mutationMeta: {
      /** Set false when the screen shows the error itself, e.g. field errors on a form. */
      errorToast?: boolean;
      /** Role named in the 403 copy, e.g. "mpesa-finance". */
      requiredRole?: string;
    };
  }
}
