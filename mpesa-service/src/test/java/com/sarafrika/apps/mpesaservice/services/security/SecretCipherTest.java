package com.sarafrika.apps.mpesaservice.services.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString("dev-only-mpesa-encryption-key-32".getBytes());
    private final SecretCipher cipher = new SecretCipher(KEY);

    @Test
    void roundTripsASecretThroughVersionedCiphertext() {
        String sealed = cipher.encrypt("bfb279f9aa9bdbcf158e97dd71a467cd");

        assertThat(sealed).startsWith("enc:v1:").doesNotContain("bfb279f9");
        assertThat(cipher.decrypt(sealed)).isEqualTo("bfb279f9aa9bdbcf158e97dd71a467cd");
        assertThat(cipher.encrypt("bfb279f9aa9bdbcf158e97dd71a467cd")).isNotEqualTo(sealed);
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void legacyPlaintextPassesThrough() {
        assertThat(cipher.decrypt("plain-consumer-secret")).isEqualTo("plain-consumer-secret");
        assertThat(cipher.decrypt("")).isEmpty();
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String sealed = cipher.encrypt("secret");
        byte[] bytes = Base64.getDecoder().decode(sealed.substring(SecretCipher.PREFIX.length()));
        bytes[bytes.length - 1] ^= 1;
        String tampered = SecretCipher.PREFIX + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tampered");
        assertThatThrownBy(() -> cipher.decrypt(SecretCipher.PREFIX + "AAAA")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherKeyCannotDecrypt() {
        String sealed = cipher.encrypt("secret");
        SecretCipher other = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));

        assertThatThrownBy(() -> other.decrypt(sealed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMissingOrWrongLengthKeyFailsWithAClearMessage() {
        assertThatThrownBy(() -> new SecretCipher("")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MPESA_ENCRYPTION_KEY").hasMessageContaining("not set");
        assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes").hasMessageContaining("16");
        assertThatThrownBy(() -> new SecretCipher("not base64!"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("base64");
    }
}
