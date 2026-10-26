package com.sarafrika.apps.mpesaservice.services.treasury;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Claims shortcodes due a scheduled balance query: one UPDATE pushes next_balance_query_at out, and SKIP LOCKED
 * keeps a second instance off rows being claimed, so each shortcode is asked once per interval (Postgres only). */
@Repository
@RequiredArgsConstructor
public class BalanceQueryClaims {

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional
    public List<UUID> claimDue(LocalDateTime now, LocalDateTime next, int limit) {
        String sql = """
                UPDATE mpesa_shortcodes SET next_balance_query_at = :next
                WHERE id IN (
                    SELECT id FROM mpesa_shortcodes
                    WHERE deleted_at IS NULL AND is_active
                      AND initiator_name IS NOT NULL AND initiator_name <> ''
                      AND (next_balance_query_at IS NULL OR next_balance_query_at <= :now)
                    ORDER BY next_balance_query_at NULLS FIRST, id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED)
                RETURNING uuid""";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", now).addValue("next", next).addValue("limit", limit);
        return jdbc.queryForList(sql, params, UUID.class);
    }
}
