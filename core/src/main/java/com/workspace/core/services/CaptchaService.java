package com.workspace.core.services;

public interface CaptchaService {
    /**
     * Validates reCAPTCHA token with secret key.
     * @param captchaResponse token received from client frontend
     * @return true if token is valid, false otherwise
     */
    boolean validateCaptcha(String captchaResponse);
}