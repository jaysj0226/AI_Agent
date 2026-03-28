package com.cookandroid.ai_agent.health;

import androidx.annotation.Nullable;
import androidx.health.connect.client.HealthConnectClient;

public class HealthConnectSnapshot {

    private final int sdkStatus;
    private final boolean permissionsGranted;
    private final long stepsToday;
    private final long latestSleepMinutes;
    @Nullable
    private final String errorMessage;

    public HealthConnectSnapshot(int sdkStatus,
                                 boolean permissionsGranted,
                                 long stepsToday,
                                 long latestSleepMinutes,
                                 @Nullable String errorMessage) {
        this.sdkStatus = sdkStatus;
        this.permissionsGranted = permissionsGranted;
        this.stepsToday = stepsToday;
        this.latestSleepMinutes = latestSleepMinutes;
        this.errorMessage = errorMessage;
    }

    public int getSdkStatus() {
        return sdkStatus;
    }

    public boolean isPermissionsGranted() {
        return permissionsGranted;
    }

    public long getStepsToday() {
        return stepsToday;
    }

    public long getLatestSleepMinutes() {
        return latestSleepMinutes;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isAvailable() {
        return sdkStatus == HealthConnectClient.SDK_AVAILABLE;
    }

    public boolean needsProviderUpdate() {
        return sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED;
    }

    public boolean hasRecentSleep() {
        return latestSleepMinutes > 0;
    }
}
