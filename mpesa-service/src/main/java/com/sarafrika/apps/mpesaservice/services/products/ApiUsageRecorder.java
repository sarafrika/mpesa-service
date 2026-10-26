package com.sarafrika.apps.mpesaservice.services.products;

import com.sarafrika.apps.mpesaservice.repositories.ClientApiUsageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Counts a product's machine API calls per Nairobi day; one upsert per call, never failing the request. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiUsageRecorder {

    static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private final ClientApiUsageRepository repository;
    private final Clock clock = Clock.systemUTC();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String clientId, boolean error) {
        Instant now = clock.instant();
        repository.increment(clientId, LocalDate.ofInstant(now, NAIROBI), error ? 1 : 0, now);
    }
}
