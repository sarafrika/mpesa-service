package com.sarafrika.apps.mpesaservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class KeycloakJwtDecoderTest {

    private static final String ISSUER = "https://signin.example.com/realms/sarafrika";
    private static final String AUDIENCE = "mpesa-service";

    private static OctetKeyPair edKey;
    private static RSAKey rsaKey;
    private static KeycloakJwtDecoder decoder;

    @BeforeAll
    static void keys() throws Exception {
        edKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID("ed").keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.EdDSA).generate();
        rsaKey = new RSAKeyGenerator(2048).keyID("rsa").keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256).generate();
        decoder = decoderWith(List.of(AUDIENCE));
    }

    @Test
    void acceptsAnEdDsaTokenForTheGateway() throws Exception {
        Jwt jwt = decoder.decode(ed(claims(ISSUER, List.of("account", AUDIENCE), 300)));

        assertThat(jwt.getSubject()).isEqualTo("service-account-elimika-payments");
        assertThat(jwt.getAudience()).contains(AUDIENCE);
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void acceptsAnRs256TokenForTheGateway() throws Exception {
        String token = sign(new RSASSASigner(rsaKey), JWSAlgorithm.RS256, "rsa", claims(ISSUER, List.of(AUDIENCE), 300));

        assertThat(decoder.decode(token).getAudience()).containsExactly(AUDIENCE);
    }

    @Test
    void rejectsARealmTokenWithoutTheGatewayAudience() throws Exception {
        String token = ed(claims(ISSUER, List.of("account"), 300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void acceptsAnyAudienceWhenNoneIsRequired() throws Exception {
        String token = ed(claims(ISSUER, List.of("account"), 300));

        assertThat(decoderWith(List.of("")).decode(token).getAudience()).containsExactly("account");
    }

    @Test
    void rejectsAnotherIssuer() throws Exception {
        String token = ed(claims("https://elsewhere.example.com/realms/x", List.of(AUDIENCE), 300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        String token = ed(claims(ISSUER, List.of(AUDIENCE), -600));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsATokenSignedByAnUnknownKey() throws Exception {
        OctetKeyPair stranger = new OctetKeyPairGenerator(Curve.Ed25519).keyID("ed").generate();
        String token = sign(new Ed25519Signer(stranger), JWSAlgorithm.EdDSA, "ed", claims(ISSUER, List.of(AUDIENCE), 300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsAKeyIdTheRealmDoesNotPublish() throws Exception {
        String token = sign(new Ed25519Signer(edKey), JWSAlgorithm.EdDSA, "gone", claims(ISSUER, List.of(AUDIENCE), 300));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    private static KeycloakJwtDecoder decoderWith(List<String> audiences) {
        JWKSet published = new JWKSet(List.of(edKey.toPublicJWK(), rsaKey.toPublicJWK()));
        return new KeycloakJwtDecoder(new ImmutableJWKSet<>(published), ISSUER, audiences);
    }

    private static JWTClaimsSet claims(String issuer, List<String> audience, long expiresInSeconds) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("service-account-elimika-payments")
                .audience(audience)
                .issueTime(Date.from(now.minusSeconds(Math.max(0, -expiresInSeconds) + 5)))
                .expirationTime(Date.from(now.plusSeconds(expiresInSeconds)))
                .build();
    }

    private static String ed(JWTClaimsSet claims) throws Exception {
        return sign(new Ed25519Signer(edKey), JWSAlgorithm.EdDSA, "ed", claims);
    }

    private static String sign(JWSSigner signer, JWSAlgorithm alg, String kid, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(alg).keyID(kid).build(), claims);
        jwt.sign(signer);
        return jwt.serialize();
    }
}
