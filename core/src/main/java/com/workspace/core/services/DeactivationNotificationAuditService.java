package com.workspace.core.services;

public interface DeactivationNotificationAuditService {

    boolean isAlreadySent(String eventId);

    void markSent(
            String eventId,
            String assetPath,
            long deactivationTime);

    void markFailed(
            String eventId,
            String assetPath,
            String reason);
}