package com.sarafrika.apps.mpesaservice.services.treasury;

import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.BalanceQuerySource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/** After a successful sweep or top-up, asks Safaricom for fresh Account Balances of the shortcodes the money
 * moved between. It runs off the callback thread once the result is committed; a failure is only logged. */
@Slf4j
@Component
public class TransferBalanceRefresh {

    private final BalanceService balances;
    private final MpesaShortCodeRepository shortcodes;
    private final TaskExecutor pool;

    public TransferBalanceRefresh(BalanceService balances, MpesaShortCodeRepository shortcodes,
                                  @Qualifier(TreasuryConfig.REFRESH_EXECUTOR) TaskExecutor pool) {
        this.balances = balances;
        this.shortcodes = shortcodes;
        this.pool = pool;
    }

    public void afterCommit(List<UUID> shortcodeUuids) {
        Set<UUID> wanted = new LinkedHashSet<>(shortcodeUuids.stream().filter(Objects::nonNull).toList());
        if (wanted.isEmpty()) {
            return;
        }
        Runnable start = () -> {
            try {
                pool.execute(() -> wanted.forEach(this::refresh));
            } catch (RuntimeException ex) {
                log.warn("Balance refresh after a transfer was not started: {}", ex.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    start.run();
                }
            });
        } else {
            start.run();
        }
    }

    /** Same gate as the console and the schedule: an active shortcode with an initiator. */
    void refresh(UUID shortcodeUuid) {
        try {
            MpesaShortCode shortcode = shortcodes.findByUuid(shortcodeUuid).orElse(null);
            if (shortcode == null || !Boolean.TRUE.equals(shortcode.getIsActive())
                    || !StringUtils.hasText(shortcode.getInitiatorName())) {
                log.info("No balance refresh for shortcode {}: missing, inactive or without an initiator",
                        shortcodeUuid);
                return;
            }
            balances.send(shortcode, BalanceQuerySource.AFTER_TRANSFER);
        } catch (RuntimeException ex) {
            log.warn("Balance refresh for shortcode {} after a transfer failed: {}", shortcodeUuid, ex.toString());
        }
    }
}
