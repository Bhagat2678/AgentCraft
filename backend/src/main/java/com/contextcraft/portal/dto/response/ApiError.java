package com.contextcraft.portal.dto.response;

import java.util.Map;

/**
 * Standardized API error response body.
 */
public class ApiError {

    private int status;
    private String error;
    private String message;
    private Map<String, String> fieldErrors; // For @Valid validation failures

    public ApiError(int status, String error, String message) {
        this.status = status;
        this.error = error;
        this.message = message;
    }

    public ApiError(int status, String error, String message, Map<String, String> fieldErrors) {
        this(status, error, message);
        this.fieldErrors = fieldErrors;
    }

    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
