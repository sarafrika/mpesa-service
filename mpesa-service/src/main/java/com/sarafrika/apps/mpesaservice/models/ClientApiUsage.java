package com.sarafrika.apps.mpesaservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A product's machine API calls on one day; rows are upserted by ApiUsageRecorder. */
@Entity
@Table(name = "client_api_usage")
@Getter
@Setter
@NoArgsConstructor
public class ClientApiUsage {

    @EmbeddedId
    private Key key;

    @Column(name = "calls", nullable = false)
    private long calls;

    @Column(name = "errors", nullable = false)
    private long errors;

    @Column(name = "last_call_at")
    private Instant lastCallAt;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "client_id", nullable = false)
        private String clientId;

        @Column(name = "day", nullable = false)
        private LocalDate day;
    }
}
