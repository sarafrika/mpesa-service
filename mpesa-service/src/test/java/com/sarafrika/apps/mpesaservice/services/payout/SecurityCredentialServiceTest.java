package com.sarafrika.apps.mpesaservice.services.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import javax.crypto.Cipher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SecurityCredentialServiceTest {

    private static KeyPair keys;
    private static String pem;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    @Test
    void encryptsTheInitiatorPasswordWithTheEnvironmentsCertificate() throws Exception {
        MpesaShortCode shortcode = shortcode(Environment.SANDBOX);
        shortcode.setInitiatorPassword("Safaricom999!*!");

        String credential = new SecurityCredentialService(pem, "").credentialFor(shortcode);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, keys.getPrivate());
        assertThat(new String(cipher.doFinal(Base64.getDecoder().decode(credential)), StandardCharsets.UTF_8))
                .isEqualTo("Safaricom999!*!");
    }

    @Test
    void aPortalGeneratedCredentialIsUsedAsIs() {
        MpesaShortCode shortcode = shortcode(Environment.PRODUCTION);
        shortcode.setSecurityCredential(" portal-credential== ");

        assertThat(new SecurityCredentialService("", "").credentialFor(shortcode)).isEqualTo("portal-credential==");
    }

    @Test
    void refusesWhenNothingIsConfigured() {
        MpesaShortCode shortcode = shortcode(Environment.PRODUCTION);
        assertThatThrownBy(() -> new SecurityCredentialService(pem, "").credentialFor(shortcode))
                .isInstanceOf(IllegalStateException.class);
        shortcode.setInitiatorPassword("secret");
        assertThatThrownBy(() -> new SecurityCredentialService(pem, "").credentialFor(shortcode))
                .hasMessageContaining("mpesa.certificates.production");
    }

    @Test
    void theBundledSafaricomCertificatesEncryptBothEnvironments() {
        SecurityCredentialService service = new SecurityCredentialService(
                "classpath:certificates/sandbox.cer", "classpath:certificates/production.cer");
        for (Environment environment : Environment.values()) {
            MpesaShortCode shortcode = shortcode(environment);
            shortcode.setInitiatorPassword("Safaricom999!*!");
            assertThat(Base64.getDecoder().decode(service.credentialFor(shortcode))).hasSize(256);
        }
    }

    private static MpesaShortCode shortcode(Environment environment) {
        MpesaShortCode shortcode = new MpesaShortCode();
        shortcode.setShortcode("600000");
        shortcode.setEnvironment(environment);
        return shortcode;
    }
}
