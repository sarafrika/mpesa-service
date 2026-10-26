package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MpesaOutgoingPaymentRepository extends JpaRepository<MpesaOutgoingPayment, Long> {

    Optional<MpesaOutgoingPayment> findByUuid(UUID uuid);

    Optional<MpesaOutgoingPayment> findByUuidAndClientId(UUID uuid, String clientId);

    Optional<MpesaOutgoingPayment> findByClientIdAndClientReference(String clientId, String clientReference);

    Optional<MpesaOutgoingPayment> findByOriginatorConversationId(String originatorConversationId);

    Optional<MpesaOutgoingPayment> findByConversationId(String conversationId);

    /** Row-locked reads, so two copies of one Safaricom result settle a payout once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MpesaOutgoingPayment p WHERE p.uuid = :uuid")
    Optional<MpesaOutgoingPayment> lockByUuid(@Param("uuid") UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MpesaOutgoingPayment p WHERE p.originatorConversationId = :id")
    Optional<MpesaOutgoingPayment> lockByOriginatorConversationId(@Param("id") String originatorConversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MpesaOutgoingPayment p WHERE p.conversationId = :id")
    Optional<MpesaOutgoingPayment> lockByConversationId(@Param("id") String conversationId);

    /** The newest settled payout of a shortcode carries the latest float balances Safaricom reported. */
    Optional<MpesaOutgoingPayment> findFirstByShortcodeUuidAndStatusAndUtilityAccountAvailableFundsIsNotNullOrderByProcessedAtDesc(
            UUID shortcodeUuid, OutgoingPaymentStatus status);
}
