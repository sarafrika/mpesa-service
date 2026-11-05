package com.sarafrika.apps.mpesaservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

/** The callback allow-list in application.yaml is what @Value actually reads, with every Safaricom IP. */
class CallbackAllowedIpsConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void everySafaricomCallbackIpIsAllowed() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new FileSystemResource("src/main/resources/application.yaml"))
                .forEach(source -> environment.getPropertySources().addLast(source));

        String raw = environment.resolveRequiredPlaceholders("${mpesa.callback.allowed-ips}");
        List<String> ips = DefaultConversionService.getSharedInstance().convert(raw, List.class);

        assertThat(ips).contains("196.201.212.69", "196.201.212.74", "196.201.212.129", "196.201.212.136",
                "196.201.214.200", "196.201.213.44").hasSize(12);
    }
}
