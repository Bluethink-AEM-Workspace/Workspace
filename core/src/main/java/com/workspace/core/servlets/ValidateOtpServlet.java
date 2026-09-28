package com.workspace.core.servlets;

import com.workspace.core.services.OtpService;
import com.workspace.core.services.OtpService.OtpValidationResult;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;

@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.methods=" + HttpConstants.METHOD_POST,
        "sling.servlet.paths=/bin/workspace/verify-otp"
    }
)
public class ValidateOtpServlet extends SlingAllMethodsServlet {

    @Reference
    private OtpService otpService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String inputOtp = request.getParameter("otp");

        if (inputOtp == null || inputOtp.trim().isEmpty()) {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"status\":\"error\", \"message\":\"Please enter the OTP.\"}");
            return;
        }

        // Validate OTP with 3 Max Attempt Limit
        OtpValidationResult result = otpService.validateOtp(request, inputOtp.trim(), 3);

        switch (result) {
            case SUCCESS:
                response.setStatus(SlingHttpServletResponse.SC_OK);
                response.getWriter().write("{\"status\":\"success\", \"message\":\"OTP verified successfully!\"}");
                break;

            case INVALID:
                response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"status\":\"error\", \"message\":\"Invalid OTP. Please try again.\"}");
                break;

            case EXPIRED:
                response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"status\":\"error\", \"message\":\"OTP has expired. Please request a new OTP.\"}");
                break;

            case MAX_ATTEMPTS_EXCEEDED:
                response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"status\":\"error\", \"message\":\"Maximum attempts exceeded. Please click Resend OTP.\"}");
                break;

            default:
                response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"status\":\"error\", \"message\":\"An unexpected error occurred.\"}");
                break;
        }
    }
}