package com.workspace.core.models;

import java.util.List;

public interface OtpVerificationModel {
    String getFormTitle();
    int getOtpLength();
    int getTimerSeconds();
    int getOtpExpirySeconds();
    int getMaxRetryAttempts();
    String getCaptchaSiteKey();
    String getGenerateEndpoint();
    String getVerifyEndpoint();
    String getInvalidOtpMsg();
    String getExpiredOtpMsg();
    String getMaxRetriesMsg();
    String getSuccessMsg();
    String getConfigJson();
    List<FormField> getFormFields();
}