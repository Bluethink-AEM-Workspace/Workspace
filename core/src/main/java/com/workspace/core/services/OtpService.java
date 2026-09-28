package com.workspace.core.services;

import org.apache.sling.api.SlingHttpServletRequest;

public interface OtpService {

    /**
     * Generates a numeric OTP, sends it via email, and stores it in the HTTP Session.
     */
    boolean generateAndSendOtp(SlingHttpServletRequest request, String target, int length, int expirySeconds);

    /**
     * Validates input OTP against session-stored OTP with attempt limits and expiry check.
     */
    OtpValidationResult validateOtp(SlingHttpServletRequest request, String inputOtp, int maxRetryAttempts);

    /**
     * Validation Result Statuses
     */
    enum OtpValidationResult {
        SUCCESS,
        INVALID,
        EXPIRED,
        MAX_ATTEMPTS_EXCEEDED,
        ERROR
    }
}