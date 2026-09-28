package com.workspace.core.models;

import com.workspace.core.models.impl.FormFieldImpl;
import com.workspace.core.models.impl.OtpVerificationModelImpl;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
class OtpVerificationModelImplTest {

    private final AemContext context = new AemContext();

    @BeforeEach
    void setUp() {
        context.addModelsForClasses(OtpVerificationModelImpl.class, FormFieldImpl.class);
    }

    @Test
    void testDefaultValues() {
        context.build().resource("/content/otp-default");
        context.currentResource("/content/otp-default");

        OtpVerificationModel model = context.request().adaptTo(OtpVerificationModel.class);

        assertNotNull(model);
        assertEquals("User Registration", model.getFormTitle());
        assertEquals(6, model.getOtpLength());
        assertEquals(60, model.getTimerSeconds());
        assertEquals(60, model.getOtpExpirySeconds());
        assertEquals(3, model.getMaxRetryAttempts());
        assertEquals("/bin/workspace/generate-otp", model.getGenerateEndpoint());
        assertEquals("/bin/workspace/verify-otp", model.getVerifyEndpoint());
        assertEquals("Invalid OTP! Please try again.", model.getInvalidOtpMsg());
        assertEquals("OTP has expired. Please request a new one.", model.getExpiredOtpMsg());
        assertEquals("Maximum verification attempts exceeded. Please restart.", model.getMaxRetriesMsg());
        assertEquals("Verification Successful! Welcome.", model.getSuccessMsg());
        assertNotNull(model.getConfigJson());
    }

    @Test
    void testCustomValues() {
        context.build().resource("/content/otp-custom",
                "formTitle", "Custom Title",
                "otpLength", 4,
                "timerSeconds", 120,
                "maxRetryAttempts", 5,
                "generateEndpoint", "/bin/custom/generate",
                "verifyEndpoint", "/bin/custom/verify",
                "invalidOtpMsg", "Wrong OTP",
                "expiredOtpMsg", "Expired",
                "maxRetriesMsg", "Blocked",
                "successMsg", "Done");
        context.currentResource("/content/otp-custom");

        OtpVerificationModel model = context.request().adaptTo(OtpVerificationModel.class);

        assertNotNull(model);
        assertEquals("Custom Title", model.getFormTitle());
        assertEquals(4, model.getOtpLength());
        assertEquals(120, model.getTimerSeconds());
        assertEquals(120, model.getOtpExpirySeconds());
        assertEquals(5, model.getMaxRetryAttempts());
        assertEquals("/bin/custom/generate", model.getGenerateEndpoint());
        assertEquals("/bin/custom/verify", model.getVerifyEndpoint());
        assertEquals("Wrong OTP", model.getInvalidOtpMsg());
        assertEquals("Expired", model.getExpiredOtpMsg());
        assertEquals("Blocked", model.getMaxRetriesMsg());
        assertEquals("Done", model.getSuccessMsg());
    }
}