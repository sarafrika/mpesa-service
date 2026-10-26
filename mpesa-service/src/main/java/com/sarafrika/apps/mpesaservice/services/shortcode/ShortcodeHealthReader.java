package com.sarafrika.apps.mpesaservice.services.shortcode;

import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationState;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeHealthDto;
import com.sarafrika.apps.mpesaservice.models.BalanceSnapshot;
import com.sarafrika.apps.mpesaservice.models.C2bRegistration;
import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.BalanceSnapshotRepository;
import com.sarafrika.apps.mpesaservice.repositories.C2bRegistrationRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceSnapshotStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Health of many shortcodes in a handful of grouped queries, so a page of 100 rows stays cheap. */
@Component
@RequiredArgsConstructor
public class ShortcodeHealthReader {

    private final EntityManager entityManager;
    private final C2bRegistrationRepository registrations;
    private final BalanceSnapshotRepository balanceSnapshots;
    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<UUID, ShortcodeHealthDto> healthOf(List<MpesaShortCode> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = codes.stream().map(MpesaShortCode::getUuid).toList();
        Map<UUID, LocalDateTime> lastPayment = pairs(entityManager.createQuery("""
                SELECT p.shortcodeUuid, MAX(p.createdAt) FROM MpesaIncomingPayment p
                WHERE p.shortcodeUuid IN :ids GROUP BY p.shortcodeUuid""", Object[].class)
                .setParameter("ids", ids).getResultList(), LocalDateTime.class);
        Map<UUID, BigDecimal> today = pairs(entityManager.createQuery("""
                SELECT p.shortcodeUuid, SUM(p.amount) FROM MpesaIncomingPayment p
                WHERE p.shortcodeUuid IN :ids AND p.status = :status AND p.createdAt >= :start
                GROUP BY p.shortcodeUuid""", Object[].class)
                .setParameter("ids", ids).setParameter("status", IncomingPaymentStatus.SUCCESS)
                .setParameter("start", LocalDate.now(clock).atStartOfDay()).getResultList(), BigDecimal.class);
        Map<UUID, MpesaOutgoingPayment> floats = entityManager.createQuery("""
                SELECT o FROM MpesaOutgoingPayment o WHERE o.id IN (
                    SELECT MAX(o2.id) FROM MpesaOutgoingPayment o2
                    WHERE o2.shortcodeUuid IN :ids AND o2.status = :status
                      AND o2.utilityAccountAvailableFunds IS NOT NULL
                    GROUP BY o2.shortcodeUuid)""", MpesaOutgoingPayment.class)
                .setParameter("ids", ids).setParameter("status", OutgoingPaymentStatus.SUCCESS)
                .getResultList().stream()
                .collect(Collectors.toMap(MpesaOutgoingPayment::getShortcodeUuid, Function.identity(), (a, b) -> a));
        Map<UUID, BigDecimal> paidOut = pairs(entityManager.createQuery("""
                SELECT o.shortcodeUuid, SUM(o.amount) FROM MpesaOutgoingPayment o
                WHERE o.shortcodeUuid IN :ids AND o.status = :status AND o.createdAt >= :start
                GROUP BY o.shortcodeUuid""", Object[].class)
                .setParameter("ids", ids).setParameter("status", OutgoingPaymentStatus.SUCCESS)
                .setParameter("start", LocalDate.now(clock).atStartOfDay()).getResultList(), BigDecimal.class);
        // The newest payout with a result: an invalid-initiator answer there means the credentials need updating.
        Map<UUID, Integer> lastResult = pairs(entityManager.createQuery("""
                SELECT o.shortcodeUuid, o.resultCode FROM MpesaOutgoingPayment o WHERE o.id IN (
                    SELECT MAX(o2.id) FROM MpesaOutgoingPayment o2
                    WHERE o2.shortcodeUuid IN :ids AND o2.resultCode IS NOT NULL
                    GROUP BY o2.shortcodeUuid)""", Object[].class)
                .setParameter("ids", ids).getResultList(), Integer.class);
        Map<UUID, BalanceSnapshot> balances = balanceSnapshots.latestCompleted(ids, BalanceSnapshotStatus.COMPLETED)
                .stream().collect(Collectors.toMap(BalanceSnapshot::getShortcodeUuid, Function.identity(), (a, b) -> a));
        Map<UUID, C2bRegistration> c2b = registrations.findLatestFor(ids).stream()
                .collect(Collectors.toMap(C2bRegistration::getShortcodeUuid, Function.identity(), (a, b) -> a));

        Map<UUID, ShortcodeHealthDto> result = new HashMap<>();
        for (MpesaShortCode code : codes) {
            UUID id = code.getUuid();
            C2bRegistration registration = c2b.get(id);
            MpesaOutgoingPayment latestFloat = floats.get(id);
            BalanceSnapshot balance = balances.get(id);
            // An Account Balance result newer than (or as new as) the last payout report wins.
            boolean useBalance = balance != null && (latestFloat == null || latestFloat.getProcessedAt() == null
                    || !latestFloat.getProcessedAt().isAfter(balance.getReceivedAt()));
            result.put(id, new ShortcodeHealthDto(code.getLastConnectionOk(), code.getLastConnectionCheckedAt(),
                    code.getLastConnectionLatencyMs(), code.getLastConnectionError(), c2bState(code, registration),
                    registration != null ? registration.getCreatedAt() : null, lastPayment.get(id),
                    today.getOrDefault(id, BigDecimal.ZERO),
                    useBalance ? balance.getUtilityAvailable()
                            : latestFloat != null ? latestFloat.getUtilityAccountAvailableFunds() : null,
                    useBalance ? balance.getWorkingAvailable()
                            : latestFloat != null ? latestFloat.getWorkingAccountAvailableFunds() : null,
                    useBalance ? balance.getReceivedAt() : latestFloat != null ? latestFloat.getProcessedAt() : null,
                    paidOut.getOrDefault(id, BigDecimal.ZERO), DarajaHints.forPayout(lastResult.get(id))));
        }
        return result;
    }

    static C2bRegistrationState c2bState(MpesaShortCode code, C2bRegistration latest) {
        if (code.getShortcodeType() == ShortcodeType.B2C) {
            return C2bRegistrationState.NOT_APPLICABLE;
        }
        if (latest == null) {
            return C2bRegistrationState.NOT_REGISTERED;
        }
        return latest.isSuccessful() ? C2bRegistrationState.REGISTERED : C2bRegistrationState.FAILED;
    }

    private static <T> Map<UUID, T> pairs(List<Object[]> rows, Class<T> type) {
        Map<UUID, T> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null && row[1] != null) {
                map.put((UUID) row[0], type.cast(row[1]));
            }
        }
        return map;
    }
}
