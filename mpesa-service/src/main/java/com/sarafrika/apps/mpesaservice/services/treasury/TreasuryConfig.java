package com.sarafrika.apps.mpesaservice.services.treasury;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(TreasuryProperties.class)
public class TreasuryConfig {

    public static final String REFRESH_EXECUTOR = "balanceRefreshExecutor";

    /** Balance queries after a transfer, one at a time, off the callback thread. */
    @Bean(name = REFRESH_EXECUTOR)
    TaskExecutor balanceRefreshExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("balance-refresh-");
        return executor;
    }
}
