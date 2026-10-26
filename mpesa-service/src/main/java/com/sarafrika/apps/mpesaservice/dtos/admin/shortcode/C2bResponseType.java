package com.sarafrika.apps.mpesaservice.dtos.admin.shortcode;

import io.swagger.v3.oas.annotations.media.Schema;

/** Safaricom's names: what happens to a payment when the validation URL cannot be reached. */
@Schema(enumAsRef = true)
public enum C2bResponseType {
    Completed, Cancelled
}
