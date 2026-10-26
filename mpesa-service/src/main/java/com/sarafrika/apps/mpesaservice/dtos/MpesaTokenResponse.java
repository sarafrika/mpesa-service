package com.sarafrika.apps.mpesaservice.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Authentication token response
 * Used for OAuth token generation
 */
@Schema(description = "OAuth access token response from Daraja API")
public record MpesaTokenResponse(

        @Schema(description = "Access token for API calls", example = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9...")
        @JsonProperty("access_token")
        String accessToken,

        @Schema(description = "Token expiration time in seconds", example = "3599")
        @JsonProperty("expires_in")
        Integer expiresIn,

        @Schema(description = "Token type", example = "Bearer")
        @JsonProperty("token_type")
        String tokenType
) {

    /**
     * Get the token for use in Authorization header
     */
    public String getAuthorizationHeader() {
        return tokenType + " " + accessToken;
    }

    /**
     * Check if token response is valid
     */
    public boolean isValid() {
        return accessToken != null && !accessToken.trim().isEmpty() &&
                expiresIn != null && expiresIn > 0;
    }
}