package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.MpesaOutgoingPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MpesaOutgoingPaymentRepository extends JpaRepository<MpesaOutgoingPayment, Long> {
}