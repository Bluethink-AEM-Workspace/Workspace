package com.workspace.core.models.impl;

import com.workspace.core.models.FormField;
import com.workspace.core.models.OtpVerificationModel;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import java.util.List;

@Model(
    adaptables = {SlingHttpServletRequest.class, Resource.class},
    adapters = OtpVerificationModel.class,
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class OtpVerificationModelImpl implements OtpVerificationModel {

    @ValueMapValue
    @Default(values = "User Registration")
    private String formTitle;

    @ValueMapValue
    @Default(intValues = 6)
    private int otpLength;

    @ValueMapValue
    @Default(intValues = 60)
    private int timerSeconds;

    @ValueMapValue
    @Default(intValues = 3)
    private int maxRetryAttempts;

    @ValueMapValue
    private String captchaSiteKey;

    @ValueMapValue
    @Default(values = "/bin/workspace/generate-otp")
    private String generateEndpoint;

    @ValueMapValue
    @Default(values = "/bin/workspace/verify-otp")
    private String verifyEndpoint;

    @ValueMapValue
    @Default(values = "Invalid OTP! Please try again.")
    private String invalidOtpMsg;

    @ValueMapValue
    @Default(values = "OTP has expired. Please request a new one.")
    private String expiredOtpMsg;

    @ValueMapValue
    @Default(values = "Maximum verification attempts exceeded. Please restart.")
    private String maxRetriesMsg;

    @ValueMapValue
    @Default(values = "Verification Successful! Welcome.")
    private String successMsg;

    @ChildResource(name = "formFields")
    private List<FormField> formFields;

    @Override
    public String getFormTitle() {
        return formTitle;
    }

    @Override
    public int getOtpLength() {
        return otpLength;
    }

    @Override
    public int getTimerSeconds() {
        return timerSeconds;
    }

    @Override
    public int getOtpExpirySeconds() {
        return timerSeconds;
    }

    @Override
    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    @Override
    public String getCaptchaSiteKey() {
        return captchaSiteKey;
    }

    @Override
    public String getGenerateEndpoint() {
        return generateEndpoint;
    }

    @Override
    public String getVerifyEndpoint() {
        return verifyEndpoint;
    }

    @Override
    public String getInvalidOtpMsg() {
        return invalidOtpMsg;
    }

    @Override
    public String getExpiredOtpMsg() {
        return expiredOtpMsg;
    }

    @Override
    public String getMaxRetriesMsg() {
        return maxRetriesMsg;
    }

    @Override
    public String getSuccessMsg() {
        return successMsg;
    }

    @Override
    public String getConfigJson() {
        return String.format(
            "{\"generateEndpoint\":\"%s\",\"verifyEndpoint\":\"%s\",\"otpLength\":%d,\"timerSeconds\":%d,\"maxRetryAttempts\":%d}",
            generateEndpoint, verifyEndpoint, otpLength, timerSeconds, maxRetryAttempts
        );
    }

    @Override
    public List<FormField> getFormFields() {
        return formFields;
    }
}