package com.sarafrika.apps.mpesaservice.services.approvals;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expires requests nobody decided in time and resumes runs a restart cut short; off with
 * mpesa.approvals.jobs-enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mpesa.approvals.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class ApprovalJobs {

    private final ApprovalService approvals;
    private final ApprovalRunner runner;

    @Scheduled(fixedDelayString = "${mpesa.approvals.job-interval:60s}", initialDelayString = "30s")
    public void expireAndResume() {
        try {
            approvals.expireDue();
        } catch (RuntimeException ex) {
            log.error("Approval expiry failed; it runs again on the next tick", ex);
        }
        try {
            runner.resumeDue();
        } catch (RuntimeException ex) {
            log.error("Resuming approvals failed; it runs again on the next tick", ex);
        }
    }
}
