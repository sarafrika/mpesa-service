package com.sarafrika.apps.mpesaservice.utils.converters;

import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Encrypts a secret column on write and decrypts it on read; Hibernate gets this from Spring. */
@Converter
public class EncryptedSecretConverter implements AttributeConverter<String, String> {

    private final SecretCipher cipher;

    public EncryptedSecretConverter(SecretCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
