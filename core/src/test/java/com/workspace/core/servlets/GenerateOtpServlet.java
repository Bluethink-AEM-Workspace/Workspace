package com.workspace.core.servlets;

import com.workspace.core.services.CaptchaService;
import com.workspace.core.services.OtpService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component(
        service = { Servlet.class },
        property = {
                ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=workspace/components/otpverification",
                ServletResolverConstants.SLING_SERVLET_SELECTORS + "=generate-otp",
                ServletResolverConstants.SLING_SERVLET_EXTENSIONS + "=json",
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_POST
        }
)
@ServiceDescription("Generate OTP Servlet")
public class GenerateOtpServlet extends SlingAllMethodsServlet {

    @Reference
    private transient CaptchaService captchaService;

    @Reference
    private transient OtpService otpService;

    @Override
    public void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String captchaResponse = request.getParameter("g-recaptcha-response");
        String target = request.getParameter("target");
        String otpLengthStr = request.getParameter("otpLength");
        String otpExpiryStr = request.getParameter("otpExpirySeconds");

        // Validate Captcha
        if (!captchaService.validateCaptcha(captchaResponse)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false, \"errorCode\":\"INVALID_CAPTCHA\"}");
            return;
        }

        int otpLength = otpLengthStr != null ? Integer.parseInt(otpLengthStr) : 6;
        int otpExpirySeconds = otpExpiryStr != null ? Integer.parseInt(otpExpiryStr) : 180;

        boolean sent = otpService.generateAndSendOtp(request, target, otpLength, otpExpirySeconds);

        if (sent) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"success\":true, \"message\":\"OTP generated successfully\"}");
        } else {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false, \"errorCode\":\"OTP_GENERATE_FAILED\"}");
        }
    }
}