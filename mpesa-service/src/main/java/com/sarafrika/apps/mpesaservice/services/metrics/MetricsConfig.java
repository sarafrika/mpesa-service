package com.sarafrika.apps.mpesaservice.services.metrics;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AlertProperties.class)
public class MetricsConfig {
}
