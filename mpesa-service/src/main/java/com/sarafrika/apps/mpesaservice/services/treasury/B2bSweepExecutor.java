package com.sarafrika.apps.mpesaservice.services.treasury;

import com.sarafrika.apps.mpesaservice.services.approvals.ApprovalExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Runs an approved B2B_SWEEP: sends the B2B payment once; the result callback settles the transfer. */
@Component
@RequiredArgsConstructor
public class B2bSweepExecutor implements ApprovalExecutor {

    private final B2bTransferService transfers;

    @Override
    public String type() {
        return B2bSweepService.TYPE;
    }

    @Override
    public Outcome execute(Approved request) {
        return transfers.execute(request);
    }
}
