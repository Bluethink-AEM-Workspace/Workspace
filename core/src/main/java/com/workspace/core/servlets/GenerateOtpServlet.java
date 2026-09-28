package com.workspace.core.servlets;

import com.workspace.core.services.OtpService;
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
        "sling.servlet.paths=/bin/workspace/generate-otp"
    }
)
public class GenerateOtpServlet extends SlingAllMethodsServlet {

    @Reference
    private OtpService otpService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Form field se target email get karein (fallback: 'email' or 'target')
        String targetEmail = request.getParameter("email");
        if (targetEmail == null || targetEmail.trim().isEmpty()) {
            targetEmail = request.getParameter("target");
        }

        if (targetEmail == null || targetEmail.trim().isEmpty()) {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"status\":\"error\", \"message\":\"Email address is required.\"}");
            return;
        }

        // 6 Digit OTP, 60 Seconds Expiry
        boolean isGenerated = otpService.generateAndSendOtp(request, targetEmail, 6, 60);

        if (isGenerated) {
            response.setStatus(SlingHttpServletResponse.SC_OK);
            response.getWriter().write("{\"status\":\"success\", \"message\":\"OTP sent successfully to " + targetEmail + "\"}");
        } else {
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\", \"message\":\"Failed to send OTP. Please verify Day CQ Mail Service in OSGi.\"}");
        }
    }
}