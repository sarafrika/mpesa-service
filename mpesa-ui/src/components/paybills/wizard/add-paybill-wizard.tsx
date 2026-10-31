"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Check, Lock } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSession } from "next-auth/react";
import { useEffect, useId, useRef, useState } from "react";
import { Controller, type Path, type UseFormReturn, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import {
  useListProducts,
  useUpsertProductGrant,
} from "@/api/generated/endpoints/admin-products/admin-products";
import {
  useCreateShortcode,
  useUpdateShortcode,
} from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import {
  useGetShortcodeBalance,
  useUpdateShortcodeFloatThreshold,
} from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import type { ShortcodeDetail } from "@/api/generated/model";
import { CreateShortcodeBody } from "@/api/generated/zod/admin-shortcodes/admin-shortcodes.zod";
import { EmptyState } from "@/components/empty-state";
import { OptionsError } from "@/components/options-error";
import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import { FormSkeleton } from "@/components/skeletons";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
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
import { formatKes } from "@/lib/format";
import { cn } from "@/lib/utils";
import { okData, toastApiError, useInvalidateShortcodes } from "../api";
import { ResponseTypeSelect } from "../c2b-actions";
import { grantableOperations, takesAccountNumbers } from "../capabilities";
import { amountText, FormRootError, TextField, toAmount } from "../form-fields";
import {
  DEFAULT_OPERATIONS,
  INITIATOR_REQUIRED,
  initiatorRoles,
  initiatorUse,
  needsInitiator,
  needsText,
  OPERATION_INFO,
  OPERATIONS,
  TYPE_OPERATIONS,
  toggled,
} from "../operations";
import {
  CREDENTIAL_HINTS,
  createFields,
  isShortcodeType,
  SANDBOX_TEST_VALUES,
  SHORTCODE_TYPES,
  TYPE_CARDS,
  typeNoun,
  type WizardStepKey,
  wizardSteps,
} from "../shortcode-types";
import { STORE_NUMBER_HINT } from "../store-number-dialog";
import { CallbackUrlsPanel } from "./callback-urls-panel";
import { ValidationRulesSlot } from "./validation-rules-slot";
import { VerifyStep } from "./verify-step";

const required = (message: string, max: number) => z.string().trim().min(1, message).max(max);
const digits = z
  .string()
  .trim()
  .regex(/^[0-9]{5,10}$/, "5 to 10 digits");

const schema = CreateShortcodeBody.omit({ min_amount: true, max_amount: true })
  .extend({
    business_name: required("Enter the name people see in the console", 255),
    shortcode: digits,
    store_number: z.string().trim(),
    consumer_key: required("Paste the consumer key from the Daraja portal", 255),
    consumer_secret: required("Paste the consumer secret from the Daraja portal", 500),
    min_amount: amountText,
    max_amount: amountText,
    low_float_threshold: z
      .string()
      .trim()
      .refine(
        (value) => value === "" || /^\d{1,13}(\.\d{1,2})?$/.test(value),
        "Enter an amount in KES with up to 2 decimals, or leave it empty",
      ),
    response_type: z.enum(["Completed", "Cancelled"]),
    product: z.string(),
    operations: z.array(z.enum(OPERATIONS)).min(1, "Choose at least one thing it will do"),
  })
  .superRefine((values, ctx) => {
    if (INITIATOR_REQUIRED && needsInitiator(values.operations)) {
      if (!values.initiator_name?.trim())
        ctx.addIssue({
          code: "custom",
          path: ["initiator_name"],
          message: "Enter the API initiator's name",
        });
      if (!values.initiator_password?.trim() && !values.security_credential?.trim())
        ctx.addIssue({
          code: "custom",
          path: ["initiator_password"],
          message: "Enter the initiator password, or paste the security credential below",
        });
    }
    if (values.shortcode_type === "TILL" && !/^[0-9]{5,10}$/.test(values.store_number))
      ctx.addIssue({
        code: "custom",
        path: ["store_number"],
        message: "Enter the store or Head Office number, 5 to 10 digits",
      });
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

const STEP_FIELDS: Record<WizardStepKey, FormField[]> = {
  business: [
    "business_name",
    "shortcode",
    "shortcode_type",
    "store_number",
    "environment",
    "product",
  ],
  operations: ["operations"],
  credentials: [
    "consumer_key",
    "consumer_secret",
    "passkey",
    "initiator_name",
    "initiator_password",
    "security_credential",
  ],
  callbacks: ["response_type", "min_amount", "max_amount", "account_reference", "transaction_desc"],
  payouts: ["min_amount", "max_amount", "transaction_desc", "low_float_threshold"],
  verify: [],
};

const LATER = "later";

const DEFAULTS: WizardValues = {
  business_name: "",
  shortcode: "",
  shortcode_type: "PAYBILL",
  store_number: "",
  environment: "SANDBOX",
  consumer_key: "",
  consumer_secret: "",
  passkey: "",
  initiator_name: "",
  initiator_password: "",
  security_credential: "",
  min_amount: "",
  max_amount: "",
  low_float_threshold: "",
  account_reference: "",
  transaction_desc: "",
  response_type: "Cancelled",
  product: LATER,
  operations: [...DEFAULT_OPERATIONS.PAYBILL],
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
          title="Adding shortcodes needs mpesa-operator"
          description="Ask an M-Pesa admin to grant you the role if you onboard shortcodes."
          action={
            <Button variant="outline" size="sm" asChild>
              <Link href="/paybills">Back to paybills & tills</Link>
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
  const threshold = useUpdateShortcodeFloatThreshold({ mutation: { meta: { errorToast: false } } });
  const grant = useUpsertProductGrant({ mutation: { meta: { errorToast: false } } });
  const products = useListProducts({ size: 100 }, { query: { enabled: isAdmin } });
  const productOptions = okData(products.data)?.items ?? [];
  const formId = useId();
  const formRef = useRef<HTMLFormElement>(null);
  const shownStep = useRef(step);

  // Continue and Back move focus to the new step's heading, so screen readers announce it.
  useEffect(() => {
    if (shownStep.current === step) return;
    shownStep.current = step;
    formRef.current?.querySelector<HTMLElement>("h2")?.focus();
  }, [step]);
  const { errors } = form.formState;
  const kind = form.watch("shortcode_type");
  const operations = form.watch("operations");
  const environment = form.watch("environment");
  const fields = createFields(kind, operations);
  const steps = wizardSteps(kind, operations);
  const roles = initiatorRoles(operations);
  // 174379 is a paybill-style test shortcode for STK push, so tills and B2C don't get the shortcut.
  const offerSandboxValues =
    environment === "SANDBOX" && kind !== "TILL" && TYPE_OPERATIONS[kind].includes("STK_PUSH");
  const stepKey = steps[step].key;
  const noun = typeNoun(kind);
  const busy = create.isPending || update.isPending || threshold.isPending;
  const floatStatus = useGetShortcodeBalance(created?.uuid ?? "", {
    query: { enabled: Boolean(created) && stepKey === "payouts" },
  });
  const defaultThreshold = okData(floatStatus.data)?.default_low_float_threshold;

  /** Server field errors land on their inputs, and the wizard opens the earliest step with one. */
  function showServerErrors(error: unknown) {
    if (isApiError(error) && error.status === 409) {
      const store = /^Store number/i.test(error.serverMessage ?? "");
      form.setError(store ? "store_number" : "shortcode", {
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
    if (!applyFieldErrors(error, form.setError)) {
      toastApiError(error);
      return;
    }
    const indexes = (isApiError(error) ? error.fieldErrors : [])
      .map((item) => steps.findIndex((s) => STEP_FIELDS[s.key].includes(item.field as FormField)))
      .filter((index) => index >= 0);
    if (indexes.length > 0) setStep(Math.min(...indexes));
  }

  async function saveDraft(values: WizardValues) {
    const response = await create.mutateAsync({
      data: {
        shortcode: values.shortcode.trim(),
        shortcode_type: values.shortcode_type,
        store_number: fields.storeNumber ? values.store_number.trim() : undefined,
        business_name: values.business_name.trim(),
        environment: values.environment,
        consumer_key: values.consumer_key.trim(),
        consumer_secret: values.consumer_secret.trim(),
        operations: values.operations,
        passkey: fields.passkey ? blank(values.passkey) : undefined,
        initiator_name: fields.initiator ? blank(values.initiator_name) : undefined,
        initiator_password: fields.initiator ? blank(values.initiator_password) : undefined,
        security_credential: fields.initiator ? blank(values.security_credential) : undefined,
      },
    });
    const draft = okData(response);
    setCreated(draft);
    await invalidate();
    toast.success("Draft saved", {
      description: "Credentials are encrypted. Nothing is live yet.",
    });
    if (draft && isAdmin && values.product !== LATER) {
      try {
        await grant.mutateAsync({
          clientId: values.product,
          shortcodeUuid: draft.uuid,
          data: { operations: grantableOperations(draft), active: true },
        });
      } catch (error) {
        toastApiError(error, { requiredRole: "mpesa-admin" });
      }
    }
  }

  async function saveProfile(draft: ShortcodeDetail, values: WizardValues) {
    await update.mutateAsync({
      uuid: draft.uuid,
      data: {
        min_amount: toAmount(values.min_amount),
        max_amount: toAmount(values.max_amount),
        account_reference: takesAccountNumbers(draft)
          ? (values.account_reference?.trim() ?? "")
          : undefined,
        transaction_desc: values.transaction_desc?.trim() ?? "",
      },
    });
    if (stepKey === "payouts" && values.low_float_threshold.trim()) {
      await threshold.mutateAsync({
        uuid: draft.uuid,
        data: { low_float_threshold: Number(values.low_float_threshold) },
      });
    }
    await invalidate();
  }

  async function next() {
    if (stepKey !== "verify" && !(await form.trigger(STEP_FIELDS[stepKey], { shouldFocus: true })))
      return;
    const values = form.getValues();
    try {
      if (stepKey === "credentials" && !created) await saveDraft(values);
      if ((stepKey === "callbacks" || stepKey === "payouts") && created)
        await saveProfile(created, values);
    } catch (error) {
      showServerErrors(error);
      return;
    }
    setStep((current) => Math.min(steps.length - 1, current + 1));
  }

  const locked = Boolean(created);
  const last = step === steps.length - 1;

  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: "Paybills & tills", href: "/paybills" }, { label: "Add shortcode" }]}
        title="Add a shortcode"
        description="A paybill, till, B2C or One Account shortcode. Nothing goes live until the checks in the last step pass, and secrets are encrypted the moment you save them."
      />
      <div className="flex flex-wrap items-start gap-6">
        <Card className="max-w-full flex-[1_1_240px] px-5 py-4">
          <ol aria-label="Steps" className="flex flex-col">
            {steps.map((item, index) => {
              const done = index < step;
              const current = index === step;
              return (
                <li
                  key={item.key}
                  className="flex items-start gap-3 py-2.5"
                  aria-current={current ? "step" : undefined}
                >
                  <span
                    className={cn(
                      "flex size-8 shrink-0 items-center justify-center rounded-full border font-semibold text-sm",
                      done && "border-chart-1 bg-chart-1 text-primary-foreground",
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
            id={formId}
            ref={formRef}
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              if (!last) void next();
            }}
            className="flex flex-col gap-6"
          >
            {locked && step < 3 && (
              <p className="rounded-md bg-info-soft px-3 py-2 text-info text-sm">
                Saved as a draft. Change these details from the {noun}'s page.
              </p>
            )}

            {stepKey === "business" && (
              <fieldset disabled={locked} className="flex flex-col gap-5">
                <h2 tabIndex={-1} className="font-semibold text-xl outline-none">
                  Business details
                </h2>
                <Controller
                  control={form.control}
                  name="shortcode_type"
                  render={({ field, fieldState }) => (
                    <fieldset className="flex flex-col gap-3">
                      <legend className="mb-3 font-medium text-sm">
                        What kind of shortcode is it?
                      </legend>
                      <RadioGroup
                        value={field.value}
                        onValueChange={(next) => {
                          field.onChange(next);
                          if (isShortcodeType(next))
                            form.setValue("operations", [...DEFAULT_OPERATIONS[next]]);
                        }}
                        disabled={locked}
                        className="grid gap-3 sm:grid-cols-2"
                      >
                        {SHORTCODE_TYPES.map((type) => {
                          const card = TYPE_CARDS[type];
                          return (
                            <FieldLabel key={type} htmlFor={`kind-${type}`}>
                              <Field orientation="horizontal">
                                <RadioGroupItem value={type} id={`kind-${type}`} />
                                <FieldContent>
                                  <FieldTitle>{card.title}</FieldTitle>
                                  <FieldDescription>{card.does}</FieldDescription>
                                  <FieldDescription className="text-xs">
                                    Needs: {card.needs}
                                  </FieldDescription>
                                </FieldContent>
                              </Field>
                            </FieldLabel>
                          );
                        })}
                      </RadioGroup>
                      <FieldError errors={[fieldState.error]} />
                    </fieldset>
                  )}
                />
                <div className="grid gap-4 sm:grid-cols-2">
                  <TextField
                    label="Display name"
                    placeholder="Elimika Fees"
                    className="sm:col-span-2"
                    error={errors.business_name}
                    {...form.register("business_name")}
                  />
                  <TextField
                    label={fields.storeNumber ? "Till number" : "Shortcode"}
                    inputMode="numeric"
                    placeholder={fields.storeNumber ? "123456" : "600100"}
                    inputClassName="font-mono"
                    description={
                      fields.storeNumber ? "The number customers pay with Buy Goods." : undefined
                    }
                    error={errors.shortcode}
                    {...form.register("shortcode")}
                  />
                  {fields.storeNumber && (
                    <TextField
                      label="Store or Head Office number"
                      inputMode="numeric"
                      placeholder="654321"
                      inputClassName="font-mono"
                      description={STORE_NUMBER_HINT}
                      error={errors.store_number}
                      {...form.register("store_number")}
                    />
                  )}
                </div>
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
                {offerSandboxValues && (
                  <div className="flex flex-wrap items-center gap-3 rounded-lg border bg-muted/40 p-3.5">
                    <p className="min-w-0 flex-[1_1_18rem] text-muted-foreground text-sm">
                      Fills Safaricom's test shortcode {SANDBOX_TEST_VALUES.shortcode} and its
                      published passkey, and turns on STK push. The consumer key and secret still
                      come from your own sandbox app on the Daraja portal.
                    </p>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => {
                        form.setValue("shortcode", SANDBOX_TEST_VALUES.shortcode, {
                          shouldValidate: true,
                        });
                        form.setValue("passkey", SANDBOX_TEST_VALUES.passkey);
                        form.setValue(
                          "operations",
                          toggled(form.getValues("operations"), "STK_PUSH", true),
                        );
                      }}
                    >
                      Use Safaricom's sandbox test values
                    </Button>
                  </div>
                )}
                {isAdmin && (
                  <Controller
                    control={form.control}
                    name="product"
                    render={({ field }) => (
                      <Field className="sm:max-w-xs">
                        <FieldLabel htmlFor="product">Sarafrika product</FieldLabel>
                        <Select
                          value={field.value}
                          onValueChange={field.onChange}
                          disabled={locked}
                        >
                          <SelectTrigger id="product" className="w-full">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {productOptions.map((option) => (
                              <SelectItem key={option.client_id} value={option.client_id}>
                                {option.display_name}
                              </SelectItem>
                            ))}
                            <SelectItem value={LATER}>Not assigned yet</SelectItem>
                          </SelectContent>
                        </Select>
                        <FieldDescription>
                          Gets access to what this {noun} can do once the draft is saved; change it
                          on the Product access tab.
                        </FieldDescription>
                        <OptionsError what="the products" query={products} />
                      </Field>
                    )}
                  />
                )}
              </fieldset>
            )}

            {stepKey === "operations" && (
              <fieldset disabled={locked} className="flex flex-col gap-5">
                <h2 tabIndex={-1} className="font-semibold text-xl outline-none">
                  What will it do?
                </h2>
                <p className="text-muted-foreground text-sm">
                  Pick only what you will use: the next step asks for just the credentials these
                  need. You can change this later on the {noun}'s Operations tab.
                </p>
                <Controller
                  control={form.control}
                  name="operations"
                  render={({ field, fieldState }) => (
                    <fieldset className="flex flex-col gap-3">
                      <legend className="sr-only">Operations</legend>
                      <div className="grid gap-3 sm:grid-cols-2">
                        {TYPE_OPERATIONS[kind].map((operation) => {
                          const info = OPERATION_INFO[operation];
                          const id = `operation-${operation}`;
                          return (
                            <FieldLabel key={operation} htmlFor={id}>
                              <Field orientation="horizontal">
                                <Checkbox
                                  id={id}
                                  checked={field.value.includes(operation)}
                                  disabled={locked}
                                  onCheckedChange={(checked) =>
                                    field.onChange(
                                      toggled(field.value, operation, checked === true),
                                    )
                                  }
                                />
                                <FieldContent>
                                  <FieldTitle>{info.label}</FieldTitle>
                                  <FieldDescription>{info.does}</FieldDescription>
                                  <FieldDescription className="text-xs">
                                    {needsText(operation)}
                                  </FieldDescription>
                                </FieldContent>
                              </Field>
                            </FieldLabel>
                          );
                        })}
                      </div>
                      <FieldError errors={[fieldState.error]} />
                    </fieldset>
                  )}
                />
              </fieldset>
            )}

            {stepKey === "credentials" && (
              <fieldset disabled={locked} className="flex flex-col gap-5">
                <h2 tabIndex={-1} className="font-semibold text-xl outline-none">
                  Daraja credentials
                </h2>
                <p className="text-muted-foreground text-sm">
                  They are write-only: after saving, only the last four characters are shown.
                  {!fields.passkey &&
                    !fields.initiator &&
                    " Nothing you chose needs a passkey or an API initiator."}
                </p>
                <div className="grid gap-4 sm:grid-cols-2">
                  <TextField
                    label="Consumer key"
                    type="password"
                    autoComplete="off"
                    inputClassName="font-mono"
                    description={
                      environment === "SANDBOX"
                        ? "From your own sandbox app on the Daraja portal (developer.safaricom.co.ke)."
                        : CREDENTIAL_HINTS.consumer
                    }
                    error={errors.consumer_key}
                    {...form.register("consumer_key")}
                  />
                  <TextField
                    label="Consumer secret"
                    type="password"
                    autoComplete="off"
                    inputClassName="font-mono"
                    description="From the same Daraja app as the key."
                    error={errors.consumer_secret}
                    {...form.register("consumer_secret")}
                  />
                  {fields.passkey && (
                    <TextField
                      label="Lipa na M-Pesa passkey"
                      type="password"
                      autoComplete="off"
                      className="sm:col-span-2"
                      inputClassName="font-mono"
                      description={`${
                        fields.storeNumber ? CREDENTIAL_HINTS.tillPasskey : CREDENTIAL_HINTS.passkey
                      } STK push needs it; you can add it later, before going live.`}
                      error={errors.passkey}
                      {...form.register("passkey")}
                    />
                  )}
                </div>
                {fields.initiator && (
                  <div className="flex flex-col gap-4 rounded-lg border p-4">
                    <div className="flex flex-col gap-1">
                      <h3 className="font-semibold text-sm">
                        API initiator{INITIATOR_REQUIRED ? "" : " (optional for now)"}
                      </h3>
                      <p className="text-muted-foreground text-sm">
                        {initiatorUse(operations)} On the M-PESA org portal, the API user needs{" "}
                        {roles.length === 1 ? "this role" : "these roles"}:
                      </p>
                      <ul className="list-disc pl-5 font-medium text-sm">
                        {roles.map((role) => (
                          <li key={role}>{role}</li>
                        ))}
                      </ul>
                    </div>
                    <div className="grid gap-4 sm:grid-cols-2">
                      <TextField
                        label="Initiator name"
                        autoComplete="off"
                        description={CREDENTIAL_HINTS.initiatorName}
                        error={errors.initiator_name}
                        {...form.register("initiator_name")}
                      />
                      <TextField
                        label="Initiator password"
                        type="password"
                        autoComplete="off"
                        description={
                          environment === "PRODUCTION"
                            ? `${CREDENTIAL_HINTS.initiatorPassword} ${CREDENTIAL_HINTS.productionCertificate}`
                            : CREDENTIAL_HINTS.initiatorPassword
                        }
                        error={errors.initiator_password}
                        {...form.register("initiator_password")}
                      />
                    </div>
                    <Field data-invalid={errors.security_credential ? true : undefined}>
                      <FieldLabel htmlFor="security-credential">
                        Security credential (instead of the password)
                      </FieldLabel>
                      <Textarea
                        id="security-credential"
                        rows={3}
                        autoComplete="off"
                        className="font-mono text-xs placeholder:font-sans"
                        placeholder={CREDENTIAL_HINTS.securityCredential}
                        aria-invalid={errors.security_credential ? true : undefined}
                        {...form.register("security_credential")}
                      />
                      <FieldError errors={[errors.security_credential]} />
                    </Field>
                  </div>
                )}
              </fieldset>
            )}

            {stepKey === "callbacks" && created && (
              <div className="flex flex-col gap-5">
                <h2 tabIndex={-1} className="font-semibold text-xl outline-none">
                  Callbacks and rules
                </h2>
                <CallbackUrlsPanel shortcode={created} />
                <div className="grid gap-4 sm:grid-cols-2">
                  {created.operations.includes("C2B") && (
                    <Controller
                      control={form.control}
                      name="response_type"
                      render={({ field }) => (
                        <ResponseTypeSelect value={field.value} onChange={field.onChange} />
                      )}
                    />
                  )}
                  {takesAccountNumbers(created) && (
                    <TextField
                      label="Default account reference"
                      placeholder="ELIMIKA"
                      error={errors.account_reference}
                      {...form.register("account_reference")}
                    />
                  )}
                  <LimitFields form={form} />
                </div>
              </div>
            )}

            {stepKey === "payouts" && created && (
              <div className="flex flex-col gap-5">
                <h2 tabIndex={-1} className="font-semibold text-xl outline-none">
                  Payouts
                </h2>
                <CallbackUrlsPanel shortcode={created} />
                {fields.paysOut && (
                  <p className="text-muted-foreground text-sm">
                    Safaricom posts each payout's result to the B2C result URL above, the service
                    default for every B2C shortcode. Nothing to register: each payout names it.
                  </p>
                )}
                <div className="grid gap-4 sm:grid-cols-2">
                  <TextField
                    label="Low-float alert below (KES)"
                    inputMode="decimal"
                    placeholder={defaultThreshold != null ? String(defaultThreshold) : undefined}
                    className="sm:col-span-2"
                    description={`B2C payouts are paid from the Utility account. The Overview flags this shortcode when that balance drops below this line. Leave it empty to use the service default${
                      defaultThreshold != null ? ` of ${formatKes(defaultThreshold)}` : ""
                    }.`}
                    error={errors.low_float_threshold}
                    {...form.register("low_float_threshold")}
                  />
                  <LimitFields form={form} />
                </div>
              </div>
            )}

            {stepKey === "verify" && created && (
              <VerifyStep
                uuid={created.uuid}
                responseType={form.getValues("response_type")}
                canActivate={isAdmin}
              />
            )}

            <FormRootError message={errors.root?.server?.message} />
          </form>

          {/* The rules editor is its own form, so it sits outside the wizard's form. */}
          {stepKey === "callbacks" && created?.operations.includes("C2B") && (
            <ValidationRulesSlot shortcode={created} />
          )}

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
              {!last ? (
                <Button type="submit" form={formId} disabled={busy}>
                  {busy && <Spinner />}
                  {stepKey === "credentials" && !created ? "Save and continue" : "Continue"}
                </Button>
              ) : (
                <Button
                  type="button"
                  onClick={() => created && router.push(`/paybills/${created.uuid}`)}
                >
                  Open {noun}
                </Button>
              )}
            </div>
          </div>
        </Card>
      </div>
    </>
  );
}

/** Amount limits and the default description, shared by Callbacks & rules and Payouts. */
function LimitFields({ form }: { form: UseFormReturn<WizardValues> }) {
  const { errors } = form.formState;
  return (
    <>
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
    </>
  );
}
