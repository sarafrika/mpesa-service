package com.sarafrika.apps.mpesaservice.services.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sarafrika.apps.mpesaservice.config.KeycloakRealmRoles;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Customers' phone numbers and names as the console may show them: in full to mpesa-finance and mpesa-admin,
 * otherwise masked (2547•••••123) and shortened (J. Kamau), in DTOs and inside raw callbacks and payloads. */
public final class PersonalData {

    private static final String DOTS = "•••••";
    private static final Set<String> PHONE_KEYS = Set.of("msisdn", "phonenumber", "phone_number", "phone",
            "recipient_phone_number");
    private static final Set<String> FULL_NAME_KEYS = Set.of("customer_name", "recipient_name", "recipientname");

    private PersonalData() {
    }

    /** Whether the signed-in person may see full phone numbers and names (finance or admin). */
    public static boolean visible() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream().map(a -> a.getAuthority())
                .anyMatch(a -> a.equals("ROLE_" + KeycloakRealmRoles.FINANCE) || a.equals("ROLE_" + KeycloakRealmRoles.ADMIN));
    }

    /** "254712345123" becomes "2547•••••123"; short or odd values keep nothing but the mask. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String value = phone.trim();
        return value.length() < 8 ? DOTS : value.substring(0, 4) + DOTS + value.substring(value.length() - 3);
    }

    /** "Jane Wanjiru Kamau" becomes "J. Kamau"; one name becomes its initial. */
    public static String shortName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String[] parts = name.trim().split("\\s+");
        String initial = parts[0].substring(0, 1).toUpperCase(Locale.ROOT) + ".";
        return parts.length == 1 ? initial : initial + " " + parts[parts.length - 1];
    }

    /** "Jane" becomes "J."; the last name stays, so first and last together read "J. Kamau". */
    public static String firstInitial(String firstName) {
        return firstName == null || firstName.isBlank() ? firstName
                : firstName.trim().substring(0, 1).toUpperCase(Locale.ROOT) + ".";
    }

    /** Safaricom's "254712345678 - JANE WANJIRU KAMAU" with both halves masked. */
    static String maskPublicName(String value) {
        int dash = value.indexOf(" - ");
        return dash < 0 ? maskPhone(value)
                : maskPhone(value.substring(0, dash)) + " - " + shortName(value.substring(dash + 3));
    }

    /** A copy of the JSON with phone and name values masked wherever they sit, including Daraja's
     * {"Name": "PhoneNumber", "Value": ...} and {"Key": "ReceiverPartyPublicName", "Value": ...} items. */
    public static JsonNode redact(JsonNode node) {
        if (node == null) {
            return null;
        }
        JsonNode copy = node.deepCopy();
        redactInPlace(copy);
        return copy;
    }

    private static void redactInPlace(JsonNode node) {
        if (node instanceof ArrayNode array) {
            array.forEach(PersonalData::redactInPlace);
            return;
        }
        if (!(node instanceof ObjectNode object)) {
            return;
        }
        JsonNode label = object.hasNonNull("Name") ? object.get("Name") : object.get("Key");
        if (label != null && label.isTextual() && object.has("Value")) {
            JsonNode masked = mask(label.asText(), object.get("Value"));
            if (masked != null) {
                object.set("Value", masked);
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode masked = mask(field.getKey(), field.getValue());
            if (masked != null) {
                field.setValue(masked);
            } else {
                redactInPlace(field.getValue());
            }
        }
    }

    private static JsonNode mask(String key, JsonNode value) {
        if (value == null || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String k = key.toLowerCase(Locale.ROOT);
        if (k.equals("receiverpartypublicname")) {
            return TextNode.valueOf(maskPublicName(value.asText()));
        }
        if (PHONE_KEYS.contains(k)) {
            return TextNode.valueOf(maskPhone(value.asText()));
        }
        if (k.equals("firstname") || k.equals("first_name")) {
            return TextNode.valueOf(firstInitial(value.asText()));
        }
        if (k.equals("middlename") || k.equals("middle_name")) {
            return TextNode.valueOf("");
        }
        if (FULL_NAME_KEYS.contains(k)) {
            return TextNode.valueOf(shortName(value.asText()));
        }
        return null;
    }
}
