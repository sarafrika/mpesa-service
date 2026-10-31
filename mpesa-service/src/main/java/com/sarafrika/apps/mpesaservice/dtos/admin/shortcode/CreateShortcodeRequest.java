package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeOperation;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** A new shortcode, saved as a draft. Secrets are write-only and encrypted the moment they are stored. */
@Schema(name = "CreateShortcodeRequest")
public record CreateShortcodeRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "600100")
        @NotBlank(message = "is required")
        @Pattern(regexp = "^[0-9]{5,10}$", message = "must be 5 to 10 digits")
        @JsonProperty("shortcode") String shortcode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "is required")
        @JsonProperty("shortcode_type") ShortcodeType shortcodeType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Elimika Fees")
        @NotBlank(message = "is required")
        @Size(max = 255, message = "must be at most 255 characters")
        @JsonProperty("business_name") String businessName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "PRODUCTION needs mpesa-admin")
        @NotNull(message = "is required")
        @JsonProperty("environment") Environment environment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "is required")
        @Size(max = 255, message = "must be at most 255 characters")
        @JsonProperty("consumer_key") String consumerKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "is required")
        @Size(max = 500, message = "must be at most 500 characters")
        @JsonProperty("consumer_secret") String consumerSecret,
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, description = "Lipa na M-Pesa passkey; needed for STK "
                + "push, issued for a till's store/HO number. Rejected for B2C (B2C FAQ 15: no passkey)")
        @Size(max = 500, message = "must be at most 500 characters")
        @JsonProperty("passkey") String passkey,
        @Schema(example = "elimika-api", description = "API initiator; needed before going live when reversals, "
                + "Transaction Status, Account Balance, B2C payouts or Pochi are turned on")
        @Size(max = 100, message = "must be at most 100 characters")
        @JsonProperty("initiator_name") String initiatorName,
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 255, message = "must be at most 255 characters")
        @JsonProperty("initiator_password") String initiatorPassword,
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "A security credential generated on the Daraja portal, used instead of the password")
        @Size(max = 4000, message = "must be at most 4000 characters")
        @JsonProperty("security_credential") String securityCredential,
        @Schema(example = "1.00")
        @DecimalMin(value = "1", message = "must be at least 1")
        @Digits(integer = 8, fraction = 2, message = "must have at most 8 digits and 2 decimals")
        @JsonProperty("min_amount") BigDecimal minAmount,
        @Schema(example = "250000.00")
        @DecimalMin(value = "1", message = "must be at least 1")
        @Digits(integer = 8, fraction = 2, message = "must have at most 8 digits and 2 decimals")
        @JsonProperty("max_amount") BigDecimal maxAmount,
        @Schema(example = "ELIMIKA", description = "Default STK account reference")
        @Size(max = 100, message = "must be at most 100 characters")
        @JsonProperty("account_reference") String accountReference,
        @Schema(example = "Elimika fees")
        @Size(max = 255, message = "must be at most 255 characters")
        @JsonProperty("transaction_desc") String transactionDesc,
        @Schema(example = "174300", description = "A till's store or Head Office number (the Go-Live shortcode "
                + "its passkey belongs to); required for TILL and rejected for every other type")
        @Pattern(regexp = "^[0-9]{5,10}$", message = "must be 5 to 10 digits")
        @JsonProperty("store_number") String storeNumber,
        @Schema(description = "What the shortcode will do, within what its type allows. Omitted: C2B for a paybill "
                + "or till, B2C payouts and Account Balance for B2C, all three for a One Account")
        @JsonProperty("operations") List<ShortcodeOperation> operations) {

    /** A request without a store number, for the types that have none. */
    public CreateShortcodeRequest(String shortcode, ShortcodeType shortcodeType, String businessName,
                                  Environment environment, String consumerKey, String consumerSecret, String passkey,
                                  String initiatorName, String initiatorPassword, String securityCredential,
                                  BigDecimal minAmount, BigDecimal maxAmount, String accountReference,
                                  String transactionDesc) {
        this(shortcode, shortcodeType, businessName, environment, consumerKey, consumerSecret, passkey, initiatorName,
                initiatorPassword, securityCredential, minAmount, maxAmount, accountReference, transactionDesc, null);
    }

    /** A request that leaves the operations to the type's defaults. */
    public CreateShortcodeRequest(String shortcode, ShortcodeType shortcodeType, String businessName,
                                  Environment environment, String consumerKey, String consumerSecret, String passkey,
                                  String initiatorName, String initiatorPassword, String securityCredential,
                                  BigDecimal minAmount, BigDecimal maxAmount, String accountReference,
                                  String transactionDesc, String storeNumber) {
        this(shortcode, shortcodeType, businessName, environment, consumerKey, consumerSecret, passkey, initiatorName,
                initiatorPassword, securityCredential, minAmount, maxAmount, accountReference, transactionDesc,
                storeNumber, null);
    }
}
