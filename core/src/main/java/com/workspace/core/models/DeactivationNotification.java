package com.workspace.core.models;

public class DeactivationNotification {

    private final String eventId;
    private final String assetName;
    private final String assetPath;
    private final long deactivationTime;
    private final String userId;
    private final String currentStatus;
    private final String environment;

    public DeactivationNotification(
            String eventId,
            String assetName,
            String assetPath,
            long deactivationTime,
            String userId,
            String currentStatus,
            String environment) {

        this.eventId = eventId;
        this.assetName = assetName;
        this.assetPath = assetPath;
        this.deactivationTime = deactivationTime;
        this.userId = userId;
        this.currentStatus = currentStatus;
        this.environment = environment;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAssetName() {
        return assetName;
    }

    public String getAssetPath() {
        return assetPath;
    }

    public long getDeactivationTime() {
        return deactivationTime;
    }

    public String getUserId() {
        return userId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getEnvironment() {
        return environment;
    }
}