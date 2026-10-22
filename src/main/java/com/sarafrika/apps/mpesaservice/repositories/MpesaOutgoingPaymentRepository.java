package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MpesaOutgoingPaymentRepository extends JpaRepository<MpesaOutgoingPayment, Long> {

    Optional<MpesaOutgoingPayment> findByUuid(UUID uuid);

    Optional<MpesaOutgoingPayment> findByUuidAndClientId(UUID uuid, String clientId);

    Optional<MpesaOutgoingPayment> findByClientIdAndClientReference(String clientId, String clientReference);

    Optional<MpesaOutgoingPayment> findByOriginatorConversationId(String originatorConversationId);

    Optional<MpesaOutgoingPayment> findByConversationId(String conversationId);

    /** The newest settled payout of a shortcode carries the latest float balances Safaricom reported. */
    Optional<MpesaOutgoingPayment> findFirstByShortcodeUuidAndStatusAndUtilityAccountAvailableFundsIsNotNullOrderByProcessedAtDesc(
            UUID shortcodeUuid, OutgoingPaymentStatus status);
}
