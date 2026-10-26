package com.sarafrika.apps.mpesaservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs such as the STK status job; each job has its own enable flag. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
