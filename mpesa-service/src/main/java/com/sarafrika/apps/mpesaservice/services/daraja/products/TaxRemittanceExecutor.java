package com.sarafrika.apps.mpesaservice.services.daraja.products;

import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalExecutor;
import com.sarafrika.apps.mpesaservice.utils.enums.DarajaFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Runs an approved TAX_REMITTANCE: sends it once; the result callback settles it. Not resumable: an
 * interrupted run is marked FAILED with its outcome unknown rather than risk a second send. */
@Component
@RequiredArgsConstructor
public class TaxRemittanceExecutor implements ApprovalExecutor {

    private final ProductPayoutService payouts;

    @Override
    public String type() {
        return ProductPayoutService.TAX_TYPE;
    }

    @Override
    public Outcome execute(Approved request) {
        return payouts.execute(request, DarajaFeature.TAX_REMITTANCE);
    }
}
