package com.workspace.core.services.impl;

import com.workspace.core.services.CaptchaService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component(service = CaptchaService.class, immediate = true)
public class CaptchaServiceImpl implements CaptchaService {

    private static final Logger LOG = LoggerFactory.getLogger(CaptchaServiceImpl.class);
    
    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    
    // Google reCAPTCHA Secret Key
    private static final String RECAPTCHA_SECRET = "6Ldl38otAAAAAD6e0cd_pMaRCsWm2ALtm_Re-ew2";

    @Override
    public boolean validateCaptcha(String captchaResponse) {
        if (captchaResponse == null || captchaResponse.trim().isEmpty()) {
            LOG.warn("reCAPTCHA response token is null or empty");
            return false;
        }

        try {
            URL url = new URL(RECAPTCHA_VERIFY_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String postParams = "secret=" + RECAPTCHA_SECRET + "&response=" + captchaResponse;

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = postParams.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    
                    String jsonResponse = response.toString();
                    LOG.debug("reCAPTCHA API Response: {}", jsonResponse);
                    
                    return jsonResponse.contains("\"success\": true") || jsonResponse.contains("\"success\":true");
                }
            } else {
                LOG.error("Failed to verify reCAPTCHA with Google, HTTP Status Code: {}", responseCode);
            }
        } catch (Exception e) {
            LOG.error("Exception occurred during reCAPTCHA verification", e);
        }

        return false;
    }
}