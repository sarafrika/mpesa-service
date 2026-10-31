package com.sarafrika.apps.mpesaservice.services.payout;

import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * The SecurityCredential Daraja wants on B2C and other initiator calls: the initiator password
 * encrypted (RSA, PKCS#1 v1.5) with Safaricom's public certificate for the shortcode's environment,
 * base64 encoded. A credential generated on the Daraja portal is used as is when the shortcode has one.
 */
@Service
public class SecurityCredentialService {

    private final String sandboxCertificate;
    private final String productionCertificate;

    public SecurityCredentialService(
            @Value("${mpesa.certificates.sandbox:}") String sandboxCertificate,
            @Value("${mpesa.certificates.production:}") String productionCertificate) {
        this.sandboxCertificate = sandboxCertificate;
        this.productionCertificate = productionCertificate;
    }

    /** False when mpesa.certificates.<environment> is blank, so an initiator password cannot be encrypted. */
    public boolean hasCertificate(Environment environment) {
        return StringUtils.hasText(environment == Environment.PRODUCTION ? productionCertificate : sandboxCertificate);
    }

    public String credentialFor(MpesaShortCode shortcode) {
        if (StringUtils.hasText(shortcode.getSecurityCredential())) {
            return shortcode.getSecurityCredential().trim();
        }
        if (!StringUtils.hasText(shortcode.getInitiatorPassword())) {
            throw new IllegalStateException("Shortcode " + shortcode.getShortcode()
                    + " has neither a security credential nor an initiator password for B2C");
        }
        String certificate = shortcode.getEnvironment() == Environment.PRODUCTION ? productionCertificate : sandboxCertificate;
        if (!StringUtils.hasText(certificate)) {
            throw new IllegalStateException("No Safaricom certificate configured for " + shortcode.getEnvironment()
                    + "; set mpesa.certificates." + shortcode.getEnvironment().name().toLowerCase());
        }
        return encrypt(shortcode.getInitiatorPassword(), publicKey(certificate));
    }

    static String encrypt(String password, PublicKey key) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt the initiator password", ex);
        }
    }

    /** Accepts an X.509 certificate or a public key, as PEM text or a path to a PEM/DER file. */
    static PublicKey publicKey(String configured) {
        try {
            String value = configured.trim();
            byte[] bytes = value.startsWith("-----") ? value.getBytes(StandardCharsets.US_ASCII)
                    : Files.readAllBytes(Path.of(value));
            String text = new String(bytes, StandardCharsets.US_ASCII);
            if (text.contains("BEGIN PUBLIC KEY")) {
                String base64 = text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
                return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
            }
            return CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(bytes)).getPublicKey();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read the Safaricom certificate", ex);
        }
    }
}
