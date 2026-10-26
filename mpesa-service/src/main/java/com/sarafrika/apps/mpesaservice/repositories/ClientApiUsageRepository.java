package com.sarafrika.apps.mpesaservice.repositories;

import com.sarafrika.apps.mpesaservice.models.ClientApiUsage;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientApiUsageRepository extends JpaRepository<ClientApiUsage, ClientApiUsage.Key> {

    /** One statement per call, so concurrent requests never lose a count. */
    @Modifying
    @Query(value = """
            INSERT INTO client_api_usage (client_id, day, calls, errors, last_call_at)
            VALUES (:clientId, :day, 1, :error, :at)
            ON CONFLICT (client_id, day) DO UPDATE
            SET calls = client_api_usage.calls + 1,
                errors = client_api_usage.errors + EXCLUDED.errors,
                last_call_at = GREATEST(client_api_usage.last_call_at, EXCLUDED.last_call_at)
            """, nativeQuery = true)
    int increment(@Param("clientId") String clientId, @Param("day") LocalDate day, @Param("error") int error,
                  @Param("at") Instant at);
}
