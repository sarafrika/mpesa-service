"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { FlaskConical } from "lucide-react";
import { type FormEvent, type ReactNode, useEffect, useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import {
  getGetValidationRulesQueryKey,
  useDryRunValidationRules,
  useGetValidationRules,
  useUpdateValidationRules,
} from "@/api/generated/endpoints/admin-c2b-validation/admin-c2b-validation";
import type { ValidationDryRunResult, ValidationRulesDto } from "@/api/generated/model";
import { ErrorState } from "@/components/error-state";
import { SectionCard } from "@/components/section-card";
import { FormSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { useHasRole } from "@/hooks/use-has-role";
import { applyFieldErrors, describeApiError, isApiError } from "@/lib/api-errors";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  describeRules,
  describeTried,
  fromDto,
  toRequest,
  type ValidationRulesValues,
  validationRulesSchema,
} from "./validation-rules-form";

export type ValidationRulesEditorProps = {
  shortcodeUuid: string;
  /** For the add-paybill wizard: no card around it and the dry run tucked under the form. */
  compact?: boolean;
  /** False on a till: Buy Goods payments carry no account number, so only amount rules apply. */
  accountNumbers?: boolean;
};

/** Edits how a paybill answers Safaricom's C2B validation request, with a dry run of a pretend payment. */
export function ValidationRulesEditor({
  shortcodeUuid,
  compact = false,
  accountNumbers = true,
}: ValidationRulesEditorProps) {
  const query = useGetValidationRules(shortcodeUuid);
  const rules = query.data?.status === 200 ? query.data.data : undefined;

  let body: ReactNode;
  if (!rules) {
    body = query.isError ? (
      <ErrorState
        error={query.error}
        resource="paybill"
        onRetry={() => query.refetch()}
        retrying={query.isFetching}
      />
    ) : (
      <FormSkeleton fields={5} />
    );
  } else {
    body = (
      <>
        <StaleNotice query={query} />
        <RulesForm
          key={rules.updated_at ?? "new"}
          shortcodeUuid={shortcodeUuid}
          rules={rules}
          compact={compact}
          accountNumbers={accountNumbers}
        />
      </>
    );
  }

  if (compact) return <div className="flex flex-col gap-4">{body}</div>;
  return (
    <SectionCard
      title="Validation rules"
      description={
        accountNumbers
          ? "Safaricom asks before each C2B payment completes. Without rules, every payment is accepted."
          : "Safaricom asks before each C2B payment completes. Buy Goods payments carry no account number, so only amount rules apply."
      }
    >
      {body}
    </SectionCard>
  );
}

function RulesForm({
  shortcodeUuid,
  rules,
  compact,
  accountNumbers,
}: {
  shortcodeUuid: string;
  rules: ValidationRulesDto;
  compact: boolean;
  accountNumbers: boolean;
}) {
  const id = useId();
  const canEdit = useHasRole("mpesa-operator");
  const queryClient = useQueryClient();
  const options = { accountNumbers };
  const form = useForm<ValidationRulesValues>({
    resolver: zodResolver(validationRulesSchema),
    defaultValues: fromDto(rules, options),
  });
  const {
    register,
    control,
    handleSubmit,
    watch,
    formState: { errors, isDirty },
  } = form;
  const values = watch();

  const save = useUpdateValidationRules({
    mutation: {
      meta: { errorToast: false },
      onSuccess: (response) => {
        toast.success("Validation rules saved");
        if (response.status === 200) form.reset(fromDto(response.data, options));
        queryClient.invalidateQueries({ queryKey: getGetValidationRulesQueryKey(shortcodeUuid) });
      },
      onError: (error) => {
        if (applyFieldErrors(error, form.setError)) return;
        const copy = describeApiError(error, {
          requiredRole: "mpesa-operator",
          resource: "paybill",
        });
        toast.error(copy.title, { description: copy.description });
      },
    },
  });

  const onSubmit = handleSubmit((submitted) =>
    save.mutate({ uuid: shortcodeUuid, data: toRequest(submitted, options) }),
  );

  return (
    <div className={cn("grid gap-6", !compact && "lg:grid-cols-[minmax(0,1fr)_minmax(0,22rem)]")}>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
        <fieldset disabled={!canEdit || save.isPending} className="m-0 min-w-0 border-0 p-0">
          <FieldGroup>
            <Field orientation="horizontal">
              <Controller
                control={control}
                name="active"
                render={({ field }) => (
                  <Switch
                    id={`${id}-active`}
                    checked={field.value}
                    onCheckedChange={field.onChange}
                  />
                )}
              />
              <FieldLabel htmlFor={`${id}-active`}>Check payments against these rules</FieldLabel>
            </Field>

            <div className="grid gap-5 sm:grid-cols-2">
              {accountNumbers && (
                <Field data-invalid={Boolean(errors.account_pattern)}>
                  <FieldLabel htmlFor={`${id}-pattern`}>Account number pattern</FieldLabel>
                  <Input
                    id={`${id}-pattern`}
                    className="font-mono"
                    placeholder="^ELM-[0-9]{6}$"
                    aria-invalid={Boolean(errors.account_pattern)}
                    {...register("account_pattern")}
                  />
                  <FieldDescription>
                    A regular expression. Leave empty to accept any account.
                  </FieldDescription>
                  <FieldError errors={[errors.account_pattern]} />
                </Field>
              )}
              <Field data-invalid={Boolean(errors.on_lookup_failure)}>
                <FieldLabel htmlFor={`${id}-failure`}>If the lookup can't be reached</FieldLabel>
                <Controller
                  control={control}
                  name="on_lookup_failure"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange} disabled={!canEdit}>
                      <SelectTrigger id={`${id}-failure`} className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="REJECT">Reject the payment (Cancelled)</SelectItem>
                        <SelectItem value="ACCEPT">Accept the payment (Completed)</SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                />
                <FieldError errors={[errors.on_lookup_failure]} />
              </Field>
              <Field data-invalid={Boolean(errors.min_amount)}>
                <FieldLabel htmlFor={`${id}-min`}>Minimum (KES)</FieldLabel>
                <Input
                  id={`${id}-min`}
                  inputMode="decimal"
                  placeholder="10"
                  className="tabular-nums"
                  aria-invalid={Boolean(errors.min_amount)}
                  {...register("min_amount")}
                />
                <FieldError errors={[errors.min_amount]} />
              </Field>
              <Field data-invalid={Boolean(errors.max_amount)}>
                <FieldLabel htmlFor={`${id}-max`}>Maximum (KES)</FieldLabel>
                <Input
                  id={`${id}-max`}
                  inputMode="decimal"
                  placeholder="250,000"
                  className="tabular-nums"
                  aria-invalid={Boolean(errors.max_amount)}
                  {...register("max_amount")}
                />
                <FieldError errors={[errors.max_amount]} />
              </Field>
            </div>

            <Field orientation="horizontal" className="items-start rounded-lg border p-3">
              <Controller
                control={control}
                name="use_lookup"
                render={({ field }) => (
                  <Checkbox
                    id={`${id}-lookup`}
                    checked={field.value}
                    onCheckedChange={(checked) => field.onChange(checked === true)}
                  />
                )}
              />
              <div className="flex flex-1 flex-col gap-3">
                <div className="flex flex-col gap-0.5">
                  <FieldLabel htmlFor={`${id}-lookup`}>
                    Ask the product to confirm each {accountNumbers ? "account" : "payment"}
                  </FieldLabel>
                  <FieldDescription>
                    Calls the product's lookup webhook before accepting; Safaricom waits about 8 s
                    in all.
                  </FieldDescription>
                </div>
                {values.use_lookup && (
                  <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_9rem]">
                    <Field data-invalid={Boolean(errors.lookup_url)}>
                      <FieldLabel htmlFor={`${id}-url`}>Lookup URL</FieldLabel>
                      <Input
                        id={`${id}-url`}
                        type="url"
                        className="font-mono"
                        placeholder="https://elimika.example/mpesa/accounts"
                        aria-invalid={Boolean(errors.lookup_url)}
                        {...register("lookup_url")}
                      />
                      <FieldError errors={[errors.lookup_url]} />
                    </Field>
                    <Field data-invalid={Boolean(errors.lookup_timeout_ms)}>
                      <FieldLabel htmlFor={`${id}-timeout`}>Timeout (ms)</FieldLabel>
                      <Input
                        id={`${id}-timeout`}
                        inputMode="numeric"
                        className="tabular-nums"
                        aria-invalid={Boolean(errors.lookup_timeout_ms)}
                        {...register("lookup_timeout_ms")}
                      />
                      <FieldError errors={[errors.lookup_timeout_ms]} />
                    </Field>
                  </div>
                )}
              </div>
            </Field>
          </FieldGroup>
        </fieldset>

        <ul className="flex flex-col gap-1 text-muted-foreground text-sm">
          {describeRules(values, options).map((line) => (
            <li key={line}>{line}</li>
          ))}
          <li>
            {accountNumbers
              ? "Anything else is rejected with C2B00012 (invalid account) or C2B00013 (invalid amount)."
              : "Anything else is rejected with C2B00013 (invalid amount)."}
          </li>
        </ul>

        {errors.root?.server && (
          <p role="alert" className="text-destructive text-sm">
            {errors.root.server.message}
          </p>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className="text-muted-foreground text-xs">
            {rules.configured
              ? `Last saved ${formatDateTime(rules.updated_at)}${rules.updated_by ? ` by ${rules.updated_by}` : ""}`
              : "Not saved yet"}
          </span>
          {canEdit ? (
            <div className="flex gap-2">
              <Button
                type="button"
                variant="ghost"
                disabled={!isDirty || save.isPending}
                onClick={() => form.reset(fromDto(rules, options))}
              >
                Discard
              </Button>
              <Button type="submit" disabled={save.isPending || (rules.configured && !isDirty)}>
                {save.isPending ? "Saving…" : "Save rules"}
              </Button>
            </div>
          ) : (
            <span className="text-muted-foreground text-xs">
              Changing rules needs mpesa-operator.
            </span>
          )}
        </div>
      </form>

      {canEdit && (
        <DryRunPanel
          shortcodeUuid={shortcodeUuid}
          draft={values}
          draftValid={validationRulesSchema.safeParse(values).success}
          accountNumbers={accountNumbers}
        />
      )}
    </div>
  );
}

function DryRunPanel({
  shortcodeUuid,
  draft,
  draftValid,
  accountNumbers,
}: {
  shortcodeUuid: string;
  draft: ValidationRulesValues;
  draftValid: boolean;
  accountNumbers: boolean;
}) {
  const id = useId();
  const [account, setAccount] = useState("");
  const [amount, setAmount] = useState("");
  const [msisdn, setMsisdn] = useState("");
  const [result, setResult] = useState<ValidationDryRunResult>();
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const dryRun = useDryRunValidationRules({
    mutation: {
      meta: { errorToast: false },
      onSuccess: (response) => {
        setFieldErrors({});
        setResult(response.status === 200 ? response.data : undefined);
      },
      onError: (error) => {
        setResult(undefined);
        if (isApiError(error) && error.fieldErrors.length > 0) {
          setFieldErrors(Object.fromEntries(error.fieldErrors.map((e) => [e.field, e.message])));
          return;
        }
        const copy = describeApiError(error, { requiredRole: "mpesa-operator" });
        toast.error(copy.title, { description: copy.description });
      },
    },
  });

  // A new draft makes an old answer misleading.
  const draftKey = JSON.stringify(draft);
  useEffect(() => {
    if (draftKey) setResult(undefined);
  }, [draftKey]);

  function run(event: FormEvent) {
    event.preventDefault();
    // Left empty, the service builds a sample that passes the rules and says what it tried.
    const typed = amount.replaceAll(",", "").trim();
    const value = typed ? Number(typed) : undefined;
    if (value !== undefined && (!Number.isFinite(value) || value <= 0)) {
      setFieldErrors({ amount: "Enter an amount above zero, or leave it empty" });
      return;
    }
    dryRun.mutate({
      uuid: shortcodeUuid,
      data: {
        account: accountNumbers ? account.trim() || undefined : undefined,
        amount: value,
        msisdn: msisdn.trim() || undefined,
        rules: draftValid ? toRequest(draft, { accountNumbers }) : undefined,
      },
    });
  }

  return (
    <form
      onSubmit={run}
      noValidate
      className="flex flex-col gap-4 rounded-lg border bg-muted/40 p-4"
      aria-labelledby={`${id}-title`}
    >
      <div className="flex flex-col gap-1">
        <h3 id={`${id}-title`} className="flex items-center gap-2 font-semibold text-sm">
          <FlaskConical aria-hidden className="size-4" /> Try a payment
        </h3>
        <p className="text-muted-foreground text-xs">
          Runs these rules, unsaved changes included, without touching M-Pesa. A lookup URL is
          called for real. Leave a field empty to try a sample the rules should accept.
        </p>
      </div>
      {accountNumbers && (
        <Field data-invalid={Boolean(fieldErrors.account)}>
          <FieldLabel htmlFor={`${id}-account`}>Account number (optional)</FieldLabel>
          <Input
            id={`${id}-account`}
            className="font-mono"
            value={account}
            onChange={(event) => setAccount(event.target.value)}
            placeholder="ELM-004512"
          />
          <FieldError>{fieldErrors.account}</FieldError>
        </Field>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field data-invalid={Boolean(fieldErrors.amount)}>
          <FieldLabel htmlFor={`${id}-amount`}>Amount (KES, optional)</FieldLabel>
          <Input
            id={`${id}-amount`}
            inputMode="decimal"
            className="tabular-nums"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            placeholder="4500"
          />
          <FieldError>{fieldErrors.amount}</FieldError>
        </Field>
        <Field data-invalid={Boolean(fieldErrors.msisdn)}>
          <FieldLabel htmlFor={`${id}-msisdn`}>Phone (optional)</FieldLabel>
          <Input
            id={`${id}-msisdn`}
            inputMode="tel"
            className="font-mono"
            value={msisdn}
            onChange={(event) => setMsisdn(event.target.value)}
            placeholder="254712345678"
          />
          <FieldError>{fieldErrors.msisdn}</FieldError>
        </Field>
      </div>
      <Button type="submit" variant="outline" disabled={dryRun.isPending}>
        {dryRun.isPending ? "Checking…" : "Run dry run"}
      </Button>
      {result && (
        <div role="status" className="flex flex-col gap-2 rounded-md border bg-card p-3">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge
              status={result.accepted ? "ACCEPTED" : "REJECTED"}
              tone={result.accepted ? "success" : "danger"}
            />
            <span className="font-mono text-xs">{result.result_code}</span>
          </div>
          <p className="text-muted-foreground text-xs">Tried {describeTried(result)}</p>
          <p className="text-sm">{result.reason}</p>
        </div>
      )}
    </form>
  );
}
