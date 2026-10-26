package com.sarafrika.apps.mpesaservice.services.callbacks;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CallbackInboxProperties.class)
public class CallbackInboxConfig {
}
