package com.workspace.core.services.impl;

import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import com.workspace.core.services.OtpService;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpSession;
import java.security.SecureRandom;

@Component(service = OtpService.class, immediate = true)
public class OtpServiceImpl implements OtpService {

    private static final Logger LOG = LoggerFactory.getLogger(OtpServiceImpl.class);

    private static final String SESSION_OTP_CODE = "SESSION_OTP_CODE";
    private static final String SESSION_OTP_EXPIRY = "SESSION_OTP_EXPIRY";
    private static final String SESSION_OTP_ATTEMPTS = "SESSION_OTP_ATTEMPTS";

    @Reference
    private MessageGatewayService messageGatewayService;

    @Override
    public boolean generateAndSendOtp(SlingHttpServletRequest request, String target, int length, int expirySeconds) {
        if (target == null || target.trim().isEmpty()) {
            LOG.warn("Target email destination is null or empty.");
            return false;
        }

        String otp = generateNumericOtp(length);
        boolean isSent = sendOtpEmail(target, otp, expirySeconds);

        if (isSent) {
            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_OTP_CODE, otp);
            session.setAttribute(SESSION_OTP_EXPIRY, System.currentTimeMillis() + (expirySeconds * 1000L));
            session.setAttribute(SESSION_OTP_ATTEMPTS, 0);
            LOG.info("OTP generated and sent to email: {}", target);
            return true;
        }

        return false;
    }

    @Override
    public OtpValidationResult validateOtp(SlingHttpServletRequest request, String inputOtp, int maxRetryAttempts) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return OtpValidationResult.EXPIRED;
        }

        String storedOtp = (String) session.getAttribute(SESSION_OTP_CODE);
        Long expiryTime = (Long) session.getAttribute(SESSION_OTP_EXPIRY);
        Integer attempts = (Integer) session.getAttribute(SESSION_OTP_ATTEMPTS);

        if (storedOtp == null || expiryTime == null) {
            return OtpValidationResult.EXPIRED;
        }

        if (System.currentTimeMillis() > expiryTime) {
            clearSessionOtp(session);
            return OtpValidationResult.EXPIRED;
        }

        attempts = (attempts == null) ? 1 : attempts + 1;
        session.setAttribute(SESSION_OTP_ATTEMPTS, attempts);

        if (attempts > maxRetryAttempts) {
            clearSessionOtp(session);
            return OtpValidationResult.MAX_ATTEMPTS_EXCEEDED;
        }

        if (storedOtp.equals(inputOtp)) {
            clearSessionOtp(session);
            return OtpValidationResult.SUCCESS;
        }

        return OtpValidationResult.INVALID;
    }

    private String generateNumericOtp(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private boolean sendOtpEmail(String recipientEmail, String otp, int expirySeconds) {
        try {
            HtmlEmail email = new HtmlEmail();
            email.addTo(recipientEmail);
            email.setSubject("Your OTP Verification Code");

            String htmlBody = "<div style='font-family: Arial, sans-serif; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;'>"
                    + "<h2 style='color: #007bff;'>OTP Verification</h2>"
                    + "<p>Your verification code is: <strong style='font-size: 22px; color: #333;'>" + otp + "</strong></p>"
                    + "<p>This code is valid for " + expirySeconds + " seconds.</p>"
                    + "</div>";

            email.setHtmlMsg(htmlBody);

            MessageGateway<HtmlEmail> messageGateway = messageGatewayService.getGateway(HtmlEmail.class);
            if (messageGateway != null) {
                messageGateway.send(email);
                LOG.info("OTP Email sent successfully to {}", recipientEmail);
                return true;
            } else {
                LOG.error("MessageGateway is null. Please configure Day CQ Mail Service in OSGi Console.");
            }
        } catch (EmailException e) {
            LOG.error("Failed to send OTP Email to {}", recipientEmail, e);
        }
        return false;
    }

    private void clearSessionOtp(HttpSession session) {
        session.removeAttribute(SESSION_OTP_CODE);
        session.removeAttribute(SESSION_OTP_EXPIRY);
        session.removeAttribute(SESSION_OTP_ATTEMPTS);
    }
}