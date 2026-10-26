package com.sarafrika.apps.mpesaservice.utils.enums;

/** RECEIVED while its handler runs; PROCESSED or FAILED after; REPLAYED once a replay of a failure succeeded. */
public enum CallbackStatus {
    RECEIVED, PROCESSED, FAILED, REPLAYED
}
