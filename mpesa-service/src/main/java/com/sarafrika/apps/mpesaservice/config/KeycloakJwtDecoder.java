package com.sarafrika.apps.mpesaservice.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;

/**
 * Verifies Keycloak tokens signed with EdDSA (the realm default) or RS256, which Spring's own
 * decoder cannot do for EdDSA, then checks expiry, issuer and, when configured, the audience.
 */
public class KeycloakJwtDecoder implements JwtDecoder {

    private final JWKSource<SecurityContext> keys;
    private final OAuth2TokenValidator<Jwt> validator;
    private final MappedJwtClaimSetConverter claimConverter = MappedJwtClaimSetConverter.withDefaults(Map.of());

    KeycloakJwtDecoder(JWKSource<SecurityContext> keys, String issuer, Collection<String> audiences) {
        this.keys = keys;
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(issuer));
        List<String> required = audiences.stream().filter(a -> a != null && !a.isBlank()).toList();
        if (!required.isEmpty()) {
            validators.add(new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                    aud -> aud != null && aud.stream().anyMatch(required::contains)));
        }
        this.validator = new DelegatingOAuth2TokenValidator<>(validators);
    }

    /** Keys come from the realm's certs endpoint, cached and refreshed when an unknown key ID appears. */
    public static KeycloakJwtDecoder forIssuer(String issuer, Collection<String> audiences) {
        String certs = issuer.replaceAll("/+$", "") + "/protocol/openid-connect/certs";
        try {
            return new KeycloakJwtDecoder(JWKSourceBuilder.create(URI.create(certs).toURL()).build(), issuer, audiences);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid Keycloak issuer URI: " + issuer, e);
        }
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        SignedJWT signed;
        try {
            signed = SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new BadJwtException("Malformed token", e);
        }
        JWSHeader header = signed.getHeader();
        try {
            if (!signed.verify(verifierFor(header))) {
                throw new BadJwtException("Invalid token signature");
            }
            Map<String, Object> claims = claimConverter.convert(signed.getJWTClaimsSet().toJSONObject());
            Jwt jwt = Jwt.withTokenValue(token)
                    .headers(h -> h.putAll(header.toJSONObject()))
                    .claims(c -> c.putAll(claims))
                    .build();
            OAuth2TokenValidatorResult result = validator.validate(jwt);
            if (result.hasErrors()) {
                throw new JwtValidationException(result.getErrors().iterator().next().getDescription(), result.getErrors());
            }
            return jwt;
        } catch (KeySourceException e) {
            throw new JwtException("Could not load Keycloak signing keys", e);
        } catch (JOSEException | ParseException e) {
            throw new BadJwtException("Token could not be verified", e);
        }
    }

    private JWSVerifier verifierFor(JWSHeader header) throws KeySourceException, JOSEException {
        JWSAlgorithm alg = header.getAlgorithm();
        JWKMatcher matcher = new JWKMatcher.Builder().keyID(header.getKeyID()).keyUse(KeyUse.SIGNATURE).build();
        JWK key = keys.get(new JWKSelector(matcher), null).stream()
                .filter(k -> k.getAlgorithm() == null || k.getAlgorithm().equals(alg))
                .findFirst()
                .orElseThrow(() -> new BadJwtException("No Keycloak key " + header.getKeyID() + " for " + alg));
        if (JWSAlgorithm.EdDSA.equals(alg)) {
            return new Ed25519Verifier(key.toOctetKeyPair());
        }
        if (JWSAlgorithm.RS256.equals(alg)) {
            return new RSASSAVerifier(key.toRSAKey());
        }
        throw new BadJwtException("Unsupported token algorithm " + alg);
    }
}