package com.sarafrika.apps.mpesaservice.utils.enums;

/** RECEIVED while its handler runs; PROCESSED or FAILED after; REPLAYED once a replay of a failure succeeded.
 * REJECTED: refused before any handler ran (an unknown callback URL), so there is nothing to apply or replay. */
public enum CallbackStatus {
    RECEIVED, PROCESSED, FAILED, REPLAYED, REJECTED
}
