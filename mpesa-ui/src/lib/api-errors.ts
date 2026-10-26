import type { FieldValues, Path, UseFormSetError } from "react-hook-form";

export type FieldError = { field: string; message: string };

/** The service's error body: `{status, error, message, field_errors}`. */
export type ApiErrorBody = {
  status?: number;
  error?: string;
  message?: string;
  field_errors?: FieldError[];
};

/** Thrown by `mpesaFetch`. `status` is 0 when the request never got a response. */
export class ApiError extends Error {
  readonly status: number;
  readonly body: unknown;
  readonly fieldErrors: FieldError[];
  readonly serverMessage?: string;

  constructor(status: number, body: unknown) {
    const parsed = asErrorBody(body);
    super(parsed?.message ?? (status ? `mpesa-service responded ${status}` : "Network error"));
    this.name = "ApiError";
    this.status = status;
    this.body = body;
    this.serverMessage = parsed?.message;
    this.fieldErrors = parsed?.field_errors ?? [];
  }
}

function asErrorBody(body: unknown): ApiErrorBody | undefined {
  if (!body || typeof body !== "object") return undefined;
  const value = body as Record<string, unknown>;
  const fieldErrors = Array.isArray(value.field_errors)
    ? value.field_errors.filter(
        (item): item is FieldError =>
          typeof item?.field === "string" && typeof item?.message === "string",
      )
    : undefined;
  return {
    status: typeof value.status === "number" ? value.status : undefined,
    error: typeof value.error === "string" ? value.error : undefined,
    message: typeof value.message === "string" && value.message ? value.message : undefined,
    field_errors: fieldErrors,
  };
}

export type ErrorKind =
  | "offline"
  | "unauthorized"
  | "forbidden"
  | "not_found"
  | "conflict"
  | "validation"
  | "unavailable"
  | "unknown";

export type ErrorCopy = {
  kind: ErrorKind;
  title: string;
  description: string;
  /** Worth offering "Try again": network trouble and 5xx, not 4xx. */
  retryable: boolean;
  fieldErrors: FieldError[];
};

export type DescribeOptions = {
  /** Role the action needs, named in the 403 copy. */
  requiredRole?: string;
  /** What was being loaded, for the 404 copy, e.g. "paybill". */
  resource?: string;
};

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/** True for errors a retry cannot fix (4xx), so React Query stops retrying. */
export function isClientError(error: unknown): boolean {
  return isApiError(error) && error.status >= 400 && error.status < 500;
}

/** Plain-language copy for any error a hook or mutation can throw. */
export function describeApiError(error: unknown, options: DescribeOptions = {}): ErrorCopy {
  const status = isApiError(error) ? error.status : 0;
  const server = isApiError(error) ? error.serverMessage : undefined;
  const fieldErrors = isApiError(error) ? error.fieldErrors : [];
  const base = { fieldErrors, retryable: false };

  if (isApiError(error) && status === 0) {
    return {
      ...base,
      kind: "offline",
      title: "Can't reach the M-Pesa service",
      description: "Check your connection and try again.",
      retryable: true,
    };
  }
  if (!isApiError(error)) {
    return {
      ...base,
      kind: "unknown",
      title: "Something went wrong",
      description: "The console hit an unexpected problem. Try again.",
      retryable: true,
    };
  }
  if (status === 401) {
    return {
      ...base,
      kind: "unauthorized",
      title: "Your session has ended",
      description: "Sign in again to carry on.",
    };
  }
  if (status === 403) {
    const needs = options.requiredRole ? ` (needs ${options.requiredRole})` : "";
    return {
      ...base,
      kind: "forbidden",
      title: `You don't have permission${needs}`,
      description: "Ask an M-Pesa admin to grant you the role if you need it.",
    };
  }
  if (status === 404) {
    const what = options.resource ?? "item";
    return {
      ...base,
      kind: "not_found",
      title: `This ${what} wasn't found`,
      description: server ?? "It may have been removed, or the link is wrong.",
    };
  }
  if (status === 409) {
    return {
      ...base,
      kind: "conflict",
      title: "That clashes with something that already exists",
      description: server ?? "Refresh and check the current state before trying again.",
    };
  }
  if (status === 400 || status === 422) {
    return {
      ...base,
      kind: "validation",
      title: "Some details need fixing",
      description:
        fieldErrors.length > 0
          ? fieldErrors.map((item) => `${item.field}: ${item.message}`).join("; ")
          : (server ?? "Check the form and try again."),
    };
  }
  if (status >= 500) {
    return {
      ...base,
      kind: "unavailable",
      title: "The M-Pesa service is unavailable",
      description: "It may be restarting or overloaded. Try again in a moment.",
      retryable: true,
    };
  }
  return {
    ...base,
    kind: "unknown",
    title: "The request didn't go through",
    description: server ?? `mpesa-service responded ${status}.`,
  };
}

/**
 * Puts the service's field errors on a react-hook-form form; anything that matches no field,
 * or a validation error without field details, lands on `root.server`. Returns true if it set any.
 */
export function applyFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fieldMap: Partial<Record<string, Path<T>>> = {},
): boolean {
  if (!isApiError(error) || (error.status !== 400 && error.status !== 422)) return false;
  const unmatched: string[] = [];
  for (const item of error.fieldErrors) {
    const name = fieldMap[item.field] ?? (item.field as Path<T>);
    if (name) setError(name, { type: "server", message: item.message });
    else unmatched.push(item.message);
  }
  if (error.fieldErrors.length === 0 || unmatched.length > 0) {
    const message = unmatched.join("; ") || error.serverMessage || "Check the form and try again.";
    setError("root.server" as Path<T>, { type: "server", message });
  }
  return true;
}
