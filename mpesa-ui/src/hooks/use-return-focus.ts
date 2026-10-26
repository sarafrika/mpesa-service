"use client";

import { useRef } from "react";

type FocusHandler = ((event: Event) => void) | undefined;

// Radix returns focus to a dialog's trigger, so one opened from state or the URL dropped it on <body>.
// These handlers note what had focus as the content opens and refocus it when it closes.
export function useReturnFocus(onOpenAutoFocus: FocusHandler, onCloseAutoFocus: FocusHandler) {
  const returnTo = useRef<Element | null>(null);
  return {
    onOpenAutoFocus: (event: Event) => {
      returnTo.current = document.activeElement;
      onOpenAutoFocus?.(event);
    },
    onCloseAutoFocus: (event: Event) => {
      onCloseAutoFocus?.(event);
      if (event.defaultPrevented) return;
      const target = returnTo.current;
      if (target instanceof HTMLElement && target.isConnected && target !== document.body) {
        event.preventDefault();
        target.focus();
      }
    },
  };
}
