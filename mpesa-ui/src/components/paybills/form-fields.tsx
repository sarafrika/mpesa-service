"use client";

import { type ComponentProps, type ReactNode, useId } from "react";
import type { FieldError as HookFieldError } from "react-hook-form";
import { z } from "zod";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";

type TextFieldProps = ComponentProps<typeof Input> & {
  label: ReactNode;
  description?: ReactNode;
  error?: HookFieldError;
  /** Classes for the input itself (e.g. `font-mono`); `className` styles the wrapper. */
  inputClassName?: string;
};

/** A labelled input with its hint and inline error; pass `{...register("name")}` through. */
export function TextField({
  label,
  description,
  error,
  className,
  inputClassName,
  ...input
}: TextFieldProps) {
  const id = useId();
  return (
    <Field data-invalid={error ? true : undefined} className={className}>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={description ? `${id}-hint` : undefined}
        className={inputClassName}
        {...input}
      />
      {description && <FieldDescription id={`${id}-hint`}>{description}</FieldDescription>}
      <FieldError errors={[error]} />
    </Field>
  );
}

/** Root-level server errors (no matching field) set by `applyFieldErrors` as `root.server`. */
export function FormRootError({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-md bg-destructive-soft px-3 py-2 text-destructive text-sm">
      {message}
    </p>
  );
}

/** Optional KES amount typed as text: empty, or at least 1 with up to two decimals. */
export const amountText = z
  .string()
  .trim()
  .refine(
    (value) => value === "" || (/^\d{1,8}(\.\d{1,2})?$/.test(value) && Number(value) >= 1),
    "Enter at least 1, with up to 2 decimals",
  );

export function toAmount(value: string | undefined): number | undefined {
  return value && value.trim() !== "" ? Number(value) : undefined;
}
