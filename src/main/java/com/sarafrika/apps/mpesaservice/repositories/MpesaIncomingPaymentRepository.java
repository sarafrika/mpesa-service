package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.MpesaIncomingPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MpesaIncomingPaymentRepository extends JpaRepository<MpesaIncomingPayment, Long> {
}