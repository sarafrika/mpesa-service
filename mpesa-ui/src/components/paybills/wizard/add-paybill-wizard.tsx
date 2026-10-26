"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Check, Lock } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSession } from "next-auth/react";
import { useState } from "react";
import { Controller, type Path, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import {
  useCreateShortcode,
  useUpdateShortcode,
} from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import type { ShortcodeDetail } from "@/api/generated/model";
import { CreateShortcodeBody } from "@/api/generated/zod/admin-shortcodes/admin-shortcodes.zod";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import { FormSkeleton } from "@/components/skeletons";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldError,
  FieldLabel,
  FieldTitle,
} from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { useHasRole } from "@/hooks/use-has-role";
import { applyFieldErrors, isApiError } from "@/lib/api-errors";
import { cn } from "@/lib/utils";
import { okData, toastApiError, useInvalidateShortcodes } from "../api";
import { ResponseTypeSelect } from "../c2b-actions";
import { amountText, FormRootError, TextField, toAmount } from "../form-fields";
import { snakeFieldMap } from "../paybill-health";
import { CallbackUrlsPanel } from "./callback-urls-panel";
import { ValidationRulesSlot } from "./validation-rules-slot";
import { VerifyStep } from "./verify-step";

const required = (message: string, max: number) => z.string().trim().min(1, message).max(max);

const schema = CreateShortcodeBody.omit({ min_amount: true, max_amount: true })
  .extend({
    business_name: required("Enter the name people see in the console", 255),
    shortcode: z
      .string()
      .trim()
      .regex(/^[0-9]{5,10}$/, "5 to 10 digits"),
    consumer_key: required("Paste the consumer key from the Daraja portal", 255),
    consumer_secret: required("Paste the consumer secret from the Daraja portal", 500),
    min_amount: amountText,
    max_amount: amountText,
    response_type: z.enum(["Completed", "Cancelled"]),
  })
  .superRefine((values, ctx) => {
    if (values.shortcode_type === "B2C") {
      if (!values.initiator_name?.trim())
        ctx.addIssue({
          code: "custom",
          path: ["initiator_name"],
          message: "B2C payouts need an initiator",
        });
      if (!values.initiator_password?.trim() && !values.security_credential?.trim())
        ctx.addIssue({
          code: "custom",
          path: ["initiator_password"],
          message: "Enter the initiator password or a security credential",
        });
    }
    if (
      values.min_amount &&
      values.max_amount &&
      Number(values.max_amount) <= Number(values.min_amount)
    )
      ctx.addIssue({
        code: "custom",
        path: ["max_amount"],
        message: "Must be more than the minimum",
      });
  });

export type WizardValues = z.infer<typeof schema>;
type FormField = Path<WizardValues>;

const STEPS: { title: string; sub: string; fields: FormField[] }[] = [
  {
    title: "Business",
    sub: "Name, shortcode, kind",
    fields: ["business_name", "shortcode", "shortcode_type", "environment"],
  },
  {
    title: "Credentials",
    sub: "Daraja keys and initiator",
    fields: [
      "consumer_key",
      "consumer_secret",
      "passkey",
      "initiator_name",
      "initiator_password",
      "security_credential",
    ],
  },
  {
    title: "Callbacks & rules",
    sub: "C2B URLs, limits, validation",
    fields: ["response_type", "min_amount", "max_amount", "account_reference", "transaction_desc"],
  },
  { title: "Verify", sub: "Token, C2B URLs, go live", fields: [] },
];
const ALL_FIELDS = STEPS.flatMap((step) => step.fields);
const FIELD_MAP = snakeFieldMap(ALL_FIELDS);

const KINDS = [
  { value: "PAYBILL", title: "Paybill", description: "Collect with account numbers (C2B and STK)" },
  { value: "TILL", title: "Till (Buy Goods)", description: "Collect without account numbers" },
  { value: "B2C", title: "B2C disbursement", description: "Send payouts to customers" },
] as const;

const DEFAULTS: WizardValues = {
  business_name: "",
  shortcode: "",
  shortcode_type: "PAYBILL",
  environment: "SANDBOX",
  consumer_key: "",
  consumer_secret: "",
  passkey: "",
  initiator_name: "",
  initiator_password: "",
  security_credential: "",
  min_amount: "",
  max_amount: "",
  account_reference: "",
  transaction_desc: "",
  response_type: "Cancelled",
};

const blank = (value: string | undefined) => (value?.trim() ? value.trim() : undefined);

export function AddPaybillWizard() {
  const { status } = useSession();
  const canCreate = useHasRole("mpesa-operator");
  if (status === "loading") return <FormSkeleton fields={5} />;
  if (!canCreate) {
    return (
      <SectionCard className="mx-auto w-full max-w-xl">
        <EmptyState
          icon={Lock}
          title="Adding paybills needs mpesa-operator"
          description="Ask an M-Pesa admin to grant you the role if you onboard shortcodes."
          action={
            <Button variant="outline" size="sm" asChild>
              <Link href="/paybills">Back to paybills</Link>
            </Button>
          }
        />
      </SectionCard>
    );
  }
  return <Wizard />;
}

function Wizard() {
  const router = useRouter();
  const isAdmin = useHasRole("mpesa-admin");
  const invalidate = useInvalidateShortcodes();
  const [step, setStep] = useState(0);
  const [created, setCreated] = useState<ShortcodeDetail>();
  const form = useForm<WizardValues>({ resolver: zodResolver(schema), defaultValues: DEFAULTS });
  const create = useCreateShortcode({ mutation: { meta: { errorToast: false } } });
  const update = useUpdateShortcode({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;
  const kind = form.watch("shortcode_type");
  const b2c = kind === "B2C";
  const busy = create.isPending || update.isPending;

  /** Server field errors land on their inputs, and the wizard opens the earliest step with one. */
  function showServerErrors(error: unknown) {
    if (isApiError(error) && error.status === 409) {
      form.setError("shortcode", {
        type: "server",
        message: error.serverMessage ?? "Already exists",
      });
      setStep(0);
      return;
    }
    if (
      isApiError(error) &&
      error.status === 403 &&
      form.getValues("environment") === "PRODUCTION"
    ) {
      form.setError("environment", {
        type: "server",
        message: "Production shortcodes need mpesa-admin",
      });
      setStep(0);
      return;
    }
    if (!applyFieldErrors(error, form.setError, FIELD_MAP)) {
      toastApiError(error);
      return;
    }
    const steps = (isApiError(error) ? error.fieldErrors : [])
      .map((item) =>
        STEPS.findIndex((s) =>
          s.fields.includes(FIELD_MAP[item.field] ?? (item.field as FormField)),
        ),
      )
      .filter((index) => index >= 0);
    if (steps.length > 0) setStep(Math.min(...steps));
  }

  async function next() {
    if (step < 3 && !(await form.trigger(STEPS[step].fields, { shouldFocus: true }))) return;
    const values = form.getValues();
    if (step === 1 && !created) {
      try {
        const response = await create.mutateAsync({
          data: {
            shortcode: values.shortcode.trim(),
            shortcode_type: values.shortcode_type,
            business_name: values.business_name.trim(),
            environment: values.environment,
            consumer_key: values.consumer_key.trim(),
            consumer_secret: values.consumer_secret.trim(),
            passkey: b2c ? undefined : blank(values.passkey),
            initiator_name: blank(values.initiator_name),
            initiator_password: blank(values.initiator_password),
            security_credential: blank(values.security_credential),
          },
        });
        setCreated(okData(response));
        await invalidate();
        toast.success("Draft saved", {
          description: "Credentials are encrypted. Nothing is live yet.",
        });
      } catch (error) {
        showServerErrors(error);
        return;
      }
    }
    if (step === 2 && created) {
      try {
        await update.mutateAsync({
          uuid: created.uuid,
          data: {
            min_amount: toAmount(values.min_amount),
            max_amount: toAmount(values.max_amount),
            account_reference: values.account_reference?.trim() ?? "",
            transaction_desc: values.transaction_desc?.trim() ?? "",
          },
        });
        await invalidate();
      } catch (error) {
        showServerErrors(error);
        return;
      }
    }
    setStep((current) => Math.min(3, current + 1));
  }

  const locked = Boolean(created);

  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: "Paybills & tills", href: "/paybills" }, { label: "Add paybill" }]}
        title="Add a paybill or till"
        description="Nothing goes live until the checks in step 4 pass. Secrets are encrypted the moment you save them."
      />
      <div className="flex flex-wrap items-start gap-6">
        <Card className="max-w-full flex-[1_1_240px] px-5 py-4">
          <ol aria-label="Steps" className="flex flex-col">
            {STEPS.map((item, index) => {
              const done = index < step;
              const current = index === step;
              return (
                <li
                  key={item.title}
                  className="flex items-start gap-3 py-2.5"
                  aria-current={current ? "step" : undefined}
                >
                  <span
                    className={cn(
                      "flex size-8 shrink-0 items-center justify-center rounded-full border font-semibold text-sm",
                      done && "border-chart-1 bg-chart-1 text-white",
                      current && "border-primary bg-primary text-primary-foreground",
                      !done && !current && "text-muted-foreground",
                    )}
                  >
                    {done ? <Check className="size-4" aria-label="Done" /> : index + 1}
                  </span>
                  <span className="flex flex-col gap-0.5">
                    <span className="font-semibold text-sm">{item.title}</span>
                    <span className="text-muted-foreground text-sm">{item.sub}</span>
                  </span>
                </li>
              );
            })}
          </ol>
        </Card>

        <Card className="min-w-0 flex-[999_1_520px] gap-6 px-5 py-6 md:px-7">
          <form
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              if (step < 3) void next();
            }}
            className="flex flex-col gap-6"
          >
            {locked && step < 2 && (
              <p className="rounded-md bg-info-soft px-3 py-2 text-info text-sm">
                Saved as a draft. Change these details from the paybill page.
              </p>
            )}

            {step === 0 && (
              <fieldset disabled={locked} className="flex flex-col gap-5">
                <h2 className="font-semibold text-xl">Business details</h2>
                <div className="grid gap-4 sm:grid-cols-2">
                  <TextField
                    label="Display name"
                    placeholder="Elimika Fees"
                    error={errors.business_name}
                    {...form.register("business_name")}
                  />
                  <TextField
                    label="Shortcode"
                    inputMode="numeric"
                    placeholder="600100"
                    className="font-mono"
                    error={errors.shortcode}
                    {...form.register("shortcode")}
                  />
                </div>
                <Controller
                  control={form.control}
                  name="shortcode_type"
                  render={({ field, fieldState }) => (
                    <fieldset className="flex flex-col gap-3">
                      <legend className="mb-3 font-medium text-sm">
                        What does this shortcode do?
                      </legend>
                      <RadioGroup
                        value={field.value}
                        onValueChange={field.onChange}
                        disabled={locked}
                        className="grid gap-3 sm:grid-cols-3"
                      >
                        {KINDS.map((option) => (
                          <FieldLabel key={option.value} htmlFor={`kind-${option.value}`}>
                            <Field orientation="horizontal">
                              <RadioGroupItem value={option.value} id={`kind-${option.value}`} />
                              <FieldContent>
                                <FieldTitle>{option.title}</FieldTitle>
                                <FieldDescription>{option.description}</FieldDescription>
                              </FieldContent>
                            </Field>
                          </FieldLabel>
                        ))}
                      </RadioGroup>
                      <FieldError errors={[fieldState.error]} />
                    </fieldset>
                  )}
                />
                <Controller
                  control={form.control}
                  name="environment"
                  render={({ field, fieldState }) => (
                    <Field
                      data-invalid={fieldState.error ? true : undefined}
                      className="sm:max-w-xs"
                    >
                      <FieldLabel htmlFor="environment">Environment</FieldLabel>
                      <Select value={field.value} onValueChange={field.onChange} disabled={locked}>
                        <SelectTrigger
                          id="environment"
                          className="w-full"
                          aria-invalid={fieldState.error ? true : undefined}
                        >
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="SANDBOX">Sandbox</SelectItem>
                          <SelectItem value="PRODUCTION" disabled={!isAdmin}>
                            Production{isAdmin ? "" : " (mpesa-admin only)"}
                          </SelectItem>
                        </SelectContent>
                      </Select>
                      <FieldDescription>Production needs the mpesa-admin role.</FieldDescription>
                      <FieldError errors={[fieldState.error]} />
                    </Field>
                  )}
                />
              </fieldset>
            )}

            {step === 1 && (
              <fieldset disabled={locked} className="flex flex-col gap-5">
                <h2 className="font-semibold text-xl">Daraja credentials</h2>
                <p className="text-muted-foreground text-sm">
                  From the Daraja portal app linked to this shortcode. They are write-only: after
                  saving, only the last four characters are shown.
                </p>
                <div className="grid gap-4 sm:grid-cols-2">
                  <TextField
                    label="Consumer key"
                    type="password"
                    autoComplete="off"
                    className="font-mono"
                    error={errors.consumer_key}
                    {...form.register("consumer_key")}
                  />
                  <TextField
                    label="Consumer secret"
                    type="password"
                    autoComplete="off"
                    className="font-mono"
                    error={errors.consumer_secret}
                    {...form.register("consumer_secret")}
                  />
                  {!b2c && (
                    <TextField
                      label="Lipa na M-Pesa passkey"
                      type="password"
                      autoComplete="off"
                      className="font-mono"
                      description="Needed for STK push."
                      error={errors.passkey}
                      {...form.register("passkey")}
                    />
                  )}
                  <TextField
                    label="B2C initiator name"
                    autoComplete="off"
                    placeholder={b2c ? "Required for payouts" : "Only for B2C, reversals, balance"}
                    error={errors.initiator_name}
                    {...form.register("initiator_name")}
                  />
                  <TextField
                    label="Initiator password"
                    type="password"
                    autoComplete="off"
                    description="Encrypted with Safaricom's certificate when a payout is sent."
                    error={errors.initiator_password}
                    {...form.register("initiator_password")}
                  />
                </div>
                <Field>
                  <FieldLabel htmlFor="security-credential">
                    Security credential (optional)
                  </FieldLabel>
                  <Textarea
                    id="security-credential"
                    rows={3}
                    autoComplete="off"
                    className="font-mono text-xs"
                    placeholder="Generated on the Daraja portal; used instead of the initiator password"
                    {...form.register("security_credential")}
                  />
                  <FieldError errors={[errors.security_credential]} />
                </Field>
              </fieldset>
            )}

            {step === 2 && created && (
              <div className="flex flex-col gap-5">
                <h2 className="font-semibold text-xl">Callbacks and rules</h2>
                <CallbackUrlsPanel kind={kind} />
                <div className="grid gap-4 sm:grid-cols-2">
                  {!b2c && (
                    <Controller
                      control={form.control}
                      name="response_type"
                      render={({ field }) => (
                        <ResponseTypeSelect value={field.value} onChange={field.onChange} />
                      )}
                    />
                  )}
                  <TextField
                    label="Default account reference"
                    placeholder="ELIMIKA"
                    error={errors.account_reference}
                    {...form.register("account_reference")}
                  />
                  <TextField
                    label="Minimum (KES)"
                    inputMode="decimal"
                    placeholder="1"
                    error={errors.min_amount}
                    {...form.register("min_amount")}
                  />
                  <TextField
                    label="Maximum (KES)"
                    inputMode="decimal"
                    placeholder="70000"
                    error={errors.max_amount}
                    {...form.register("max_amount")}
                  />
                  <TextField
                    label="Default description"
                    placeholder="Payment"
                    error={errors.transaction_desc}
                    {...form.register("transaction_desc")}
                  />
                </div>
                {!b2c && <ValidationRulesSlot shortcode={created} />}
              </div>
            )}

            {step === 3 && created && (
              <VerifyStep
                uuid={created.uuid}
                responseType={form.getValues("response_type")}
                canActivate={isAdmin}
              />
            )}

            <FormRootError message={errors.root?.server?.message} />

            <div className="flex flex-wrap justify-between gap-3 border-t pt-5">
              <Button
                type="button"
                variant="outline"
                disabled={step === 0 || busy}
                onClick={() => setStep((current) => Math.max(0, current - 1))}
              >
                Back
              </Button>
              <div className="flex flex-wrap gap-3">
                {created && (
                  <Button type="button" variant="outline" asChild>
                    <Link href={`/paybills/${created.uuid}`}>Save as draft</Link>
                  </Button>
                )}
                {step < 3 ? (
                  <Button type="submit" disabled={busy}>
                    {busy && <Spinner />}
                    {step === 1 && !created ? "Save and continue" : "Continue"}
                  </Button>
                ) : (
                  <Button
                    type="button"
                    onClick={() => created && router.push(`/paybills/${created.uuid}`)}
                  >
                    Open paybill
                  </Button>
                )}
              </div>
            </div>
          </form>
        </Card>
      </div>
    </>
  );
}
