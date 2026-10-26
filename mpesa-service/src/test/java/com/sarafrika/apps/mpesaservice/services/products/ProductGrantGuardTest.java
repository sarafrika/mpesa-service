package com.sarafrika.apps.mpesaservice.services.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sarafrika.apps.mpesaservice.exceptions.ProductAccessDeniedException;
import com.sarafrika.apps.mpesaservice.models.ClientShortcodeGrant;
import com.sarafrika.apps.mpesaservice.repositories.ClientShortcodeGrantRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ProductGrantGuardTest {

    private static final UUID PAYBILL = UUID.randomUUID();
    private static final UUID OTHER_PAYBILL = UUID.randomUUID();

    private final ClientShortcodeGrantRepository grants = mock(ClientShortcodeGrantRepository.class);
    private final ProductGrantGuard guard = new ProductGrantGuard(grants, true);
    private final ProductGrantGuard off = new ProductGrantGuard(grants, false);

    @BeforeEach
    void elimikaMayPushAndLookUpOnOnePaybill() {
        ClientShortcodeGrant grant = grant("elimika-backend", PAYBILL, true, GrantOperation.STK_PUSH,
                GrantOperation.STATUS_LOOKUP);
        ClientShortcodeGrant paused = grant("shop-api", PAYBILL, false, GrantOperation.STK_PUSH);
        when(grants.findByClientIdAndShortcodeUuid(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        when(grants.findByClientIdAndShortcodeUuid("elimika-backend", PAYBILL)).thenReturn(Optional.of(grant));
        when(grants.findByClientIdAndShortcodeUuid("shop-api", PAYBILL)).thenReturn(Optional.of(paused));
        when(grants.findByClientIdAndActiveTrue(anyString())).thenReturn(List.of());
        when(grants.findByClientIdAndActiveTrue("elimika-backend")).thenReturn(List.of(grant));
    }

    static Stream<Arguments> writes() {
        return Stream.of(
                Arguments.of("granted operation on the granted paybill", "elimika-backend", PAYBILL,
                        GrantOperation.STK_PUSH, true),
                Arguments.of("operation not granted", "elimika-backend", PAYBILL, GrantOperation.B2C_PAYOUT, false),
                Arguments.of("paybill not granted", "elimika-backend", OTHER_PAYBILL, GrantOperation.STK_PUSH, false),
                Arguments.of("grant paused", "shop-api", PAYBILL, GrantOperation.STK_PUSH, false),
                Arguments.of("unknown client", "stranger", PAYBILL, GrantOperation.STK_PUSH, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writes")
    void moneyMovingCallsNeedTheExactGrant(String label, String client, UUID paybill, GrantOperation op,
                                           boolean allowed) {
        if (allowed) {
            assertThatCode(() -> guard.require(product(client), paybill, op)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> guard.require(product(client), paybill, op))
                    .isInstanceOf(ProductAccessDeniedException.class)
                    .hasMessageContaining(client).hasMessageContaining(op.name());
        }
        assertThatCode(() -> off.require(product(client), paybill, op)).doesNotThrowAnyException();
    }

    @Test
    void consoleUsersStillNeedAGrantToMoveMoney() {
        assertThatThrownBy(() -> guard.require(console("mpesa-console", "mpesa-admin"), PAYBILL,
                GrantOperation.STK_PUSH)).isInstanceOf(ProductAccessDeniedException.class);
    }

    @Test
    void consoleUsersReadWithoutGrantsButProductsNeedStatusLookup() {
        Authentication viewer = console("mpesa-console", "mpesa-viewer");
        assertThatCode(() -> guard.requireReadAccess(viewer, GrantOperation.STATUS_LOOKUP)).doesNotThrowAnyException();
        assertThat(guard.canRead(viewer, OTHER_PAYBILL, GrantOperation.STATUS_LOOKUP)).isTrue();

        assertThatCode(() -> guard.requireReadAccess(product("elimika-backend"), GrantOperation.STATUS_LOOKUP))
                .doesNotThrowAnyException();
        assertThat(guard.canRead(product("elimika-backend"), PAYBILL, GrantOperation.STATUS_LOOKUP)).isTrue();
        assertThat(guard.canRead(product("elimika-backend"), OTHER_PAYBILL, GrantOperation.STATUS_LOOKUP)).isFalse();
        assertThatThrownBy(() -> guard.requireReadAccess(product("stranger"), GrantOperation.STATUS_LOOKUP))
                .isInstanceOf(ProductAccessDeniedException.class).hasMessageContaining("STATUS_LOOKUP");
        assertThatThrownBy(() -> guard.requireReadAccess(null, GrantOperation.STATUS_LOOKUP))
                .isInstanceOf(ProductAccessDeniedException.class).hasMessageContaining("anonymous");
    }

    @Test
    void aTokenWithAnUnrelatedRealmRoleIsTreatedAsAProduct() {
        assertThat(guard.canRead(console("someone", "offline_access"), PAYBILL, GrantOperation.STATUS_LOOKUP)).isFalse();
    }

    @Test
    void listsAreFilteredToReadableShortcodes() {
        List<UUID> rows = List.of(PAYBILL, OTHER_PAYBILL);

        assertThat(guard.visible(product("elimika-backend"), rows, u -> u, GrantOperation.STATUS_LOOKUP))
                .containsExactly(PAYBILL);
        assertThat(guard.visible(console("mpesa-console", "mpesa-finance"), rows, u -> u,
                GrantOperation.STATUS_LOOKUP)).containsExactly(PAYBILL, OTHER_PAYBILL);
        assertThat(off.visible(product("stranger"), rows, u -> u, GrantOperation.STATUS_LOOKUP)).hasSize(2);
    }

    private static ClientShortcodeGrant grant(String client, UUID shortcode, boolean active, GrantOperation... ops) {
        ClientShortcodeGrant grant = new ClientShortcodeGrant();
        grant.setClientId(client);
        grant.setShortcodeUuid(shortcode);
        grant.setActive(active);
        grant.setOperations(EnumSet.of(ops[0], ops));
        return grant;
    }

    static JwtAuthenticationToken product(String azp) {
        return new JwtAuthenticationToken(jwt(azp), AuthorityUtils.createAuthorityList("SCOPE_profile"));
    }

    static JwtAuthenticationToken console(String azp, String role) {
        return new JwtAuthenticationToken(jwt(azp), AuthorityUtils.createAuthorityList("ROLE_" + role));
    }

    private static Jwt jwt(String azp) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("azp", azp, "sub", "service-account-" + azp));
    }
}
