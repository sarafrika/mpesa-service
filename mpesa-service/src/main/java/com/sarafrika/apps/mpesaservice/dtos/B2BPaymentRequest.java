package com.sarafrika.apps.mpesaservice.dtos;

import java.math.BigDecimal;

/** A B2B payment from a shortcode: CommandID BusinessPayBill (to a paybill, with an account reference)
 * or BusinessBuyGoods (to a till). Amount is whole shillings. */
public record B2BPaymentRequest(String commandId, String receiverShortcode, BigDecimal amount,
                                String accountReference, String remarks) {
}
