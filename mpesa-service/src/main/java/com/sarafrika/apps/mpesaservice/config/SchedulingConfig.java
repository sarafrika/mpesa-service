package com.sarafrika.apps.mpesaservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs such as the STK status job; each job has its own enable flag, and
 * mpesa.scheduling.enabled=false switches all of them off (tests call a job's method themselves). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "mpesa.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
