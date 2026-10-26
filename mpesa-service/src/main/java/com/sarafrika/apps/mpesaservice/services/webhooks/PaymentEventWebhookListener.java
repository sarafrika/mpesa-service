package com.sarafrika.apps.mpesaservice.services.webhooks;

import com.sarafrika.apps.mpesaservice.events.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Queues webhooks only once the payment's own transaction has committed, then sends them in the background. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventWebhookListener {

    private final WebhookFanOutService fanOut;
    private final WebhookDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentEvent(PaymentEvent event) {
        try {
            if (!fanOut.enqueue(event).isEmpty()) {
                dispatcher.kick();
            }
        } catch (RuntimeException e) {
            // The payment is already committed; a webhook problem must never surface to Safaricom's callback.
            log.error("Could not queue webhooks for {} {}", event.type(), event.paymentUuid(), e);
        }
    }
}
