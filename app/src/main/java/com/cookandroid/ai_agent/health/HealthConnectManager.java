package com.cookandroid.ai_agent.health;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;

public class HealthConnectManager {

    public interface SnapshotCallback {
        void onSnapshotLoaded(@NonNull HealthConnectSnapshot snapshot);
    }

    public static final String PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata";
    private static final String ONBOARDING_URI =
            "market://details?id=" + PROVIDER_PACKAGE_NAME + "&url=healthconnect%3A%2F%2Fonboarding";
    private static final Set<String> REQUIRED_PERMISSIONS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "android.permission.health.READ_SLEEP",
                    "android.permission.health.READ_STEPS"
            ))
    );

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public int getSdkStatus(@NonNull Context context) {
        return HealthConnectClient.sdkStatus(context);
    }

    @NonNull
    public Set<String> getRequiredPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    public void loadSnapshot(@NonNull Context context, @NonNull SnapshotCallback callback) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            int sdkStatus = getSdkStatus(appContext);
            boolean permissionsGranted = false;
            String errorMessage = null;
            if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                try {
                    Set<String> grantedPermissions = queryGrantedPermissions(appContext);
                    permissionsGranted = grantedPermissions.containsAll(REQUIRED_PERMISSIONS);
                    if (!permissionsGranted) {
                        errorMessage = "Grant both Sleep and Steps permissions in Health Connect.";
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    errorMessage = "Permission query was interrupted.";
                } catch (Exception exception) {
                    errorMessage = createUserFacingError(exception);
                }
            }
            HealthConnectSnapshot snapshot =
                    new HealthConnectSnapshot(sdkStatus, permissionsGranted, 0L, 0L, errorMessage);
            mainHandler.post(() -> callback.onSnapshotLoaded(snapshot));
        }).start();
    }

    public void openProviderOnboarding(@NonNull Context context) {
        try {
            Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(ONBOARDING_URI));
            marketIntent.setPackage("com.android.vending");
            marketIntent.putExtra("overlay", true);
            marketIntent.putExtra("callerId", context.getPackageName());
            context.startActivity(marketIntent);
        } catch (ActivityNotFoundException exception) {
            Intent webIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + PROVIDER_PACKAGE_NAME));
            context.startActivity(webIntent);
        }
    }

    public void openManageData(@NonNull Context context) {
        try {
            Intent manageIntent = new Intent(HealthConnectClient.getHealthConnectSettingsAction());
            context.startActivity(manageIntent);
        } catch (ActivityNotFoundException exception) {
            openProviderOnboarding(context);
        }
    }

    @NonNull
    public PermissionController getPermissionController(@NonNull Context context) {
        return HealthConnectClient.getOrCreate(context).getPermissionController();
    }

    @NonNull
    private Set<String> queryGrantedPermissions(@NonNull Context context) throws InterruptedException {
        PermissionController permissionController = getPermissionController(context);
        @SuppressWarnings("unchecked")
        Set<String> grantedPermissions = (Set<String>) BuildersKt.runBlocking(
                EmptyCoroutineContext.INSTANCE,
                new Function2<CoroutineScope, Continuation<? super Set<String>>, Object>() {
                    @Override
                    public Object invoke(CoroutineScope coroutineScope,
                                         Continuation<? super Set<String>> continuation) {
                        return permissionController.getGrantedPermissions(continuation);
                    }
                }
        );
        return grantedPermissions == null ? Collections.emptySet() : grantedPermissions;
    }

    @NonNull
    private String createUserFacingError(@NonNull Exception exception) {
        if (exception instanceof SecurityException) {
            return "Health Connect denied the permission query. Reopen the permission screen and allow Sleep and Steps.";
        }
        String message = exception.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return exception.getClass().getSimpleName() + ": " + message.trim();
        }
        return exception.getClass().getSimpleName();
    }
}
