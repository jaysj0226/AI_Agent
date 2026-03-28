package com.cookandroid.ai_agent.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.health.connect.client.PermissionController;

import com.cookandroid.ai_agent.R;
import com.cookandroid.ai_agent.ai.AiGatewayClient;
import com.cookandroid.ai_agent.data.CheckInEntry;
import com.cookandroid.ai_agent.data.WellnessRepository;
import com.cookandroid.ai_agent.databinding.FragmentSettingsBinding;
import com.cookandroid.ai_agent.health.HealthConnectManager;
import com.cookandroid.ai_agent.health.HealthConnectSnapshot;
import com.cookandroid.ai_agent.ui.UiFormatters;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Set;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private WellnessRepository repository;
    private final HealthConnectManager healthConnectManager = new HealthConnectManager();
    private final AiGatewayClient aiGatewayClient = new AiGatewayClient();
    private HealthConnectSnapshot healthConnectSnapshot;
    private final ActivityResultLauncher<Set<String>> permissionsLauncher =
            registerForActivityResult(
                    PermissionController.createRequestPermissionResultContract(),
                    grantedPermissions -> {
                        if (binding == null) {
                            return;
                        }

                        int messageResId = grantedPermissions.containsAll(
                                healthConnectManager.getRequiredPermissions()
                        ) ? R.string.health_connect_permissions_granted
                                : R.string.health_connect_permissions_denied;
                        String detail = grantedPermissions.isEmpty()
                                ? "\nPermission result was empty. Open Health Connect and allow both Sleep and Steps."
                                : "";
                        Snackbar.make(
                                binding.getRoot(),
                                getString(messageResId) + detail,
                                Snackbar.LENGTH_LONG
                        ).show();
                        bindState();
                    }
            );

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        repository = new WellnessRepository(requireContext());
        binding.buttonClearData.setOnClickListener(view -> confirmClearData());
        binding.buttonHealthConnectAction.setOnClickListener(view -> handleHealthConnectAction());
        binding.buttonHealthConnectManage.setOnClickListener(view -> openHealthConnectManage());
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        bindState();
    }

    private void bindState() {
        List<CheckInEntry> entries = repository.getEntries();
        binding.textSettingsStorageBody.setText(R.string.settings_storage_body);
        if (aiGatewayClient.isConfigured()) {
            binding.textSettingsAiBody.setText(getString(
                    R.string.settings_ai_body_configured,
                    aiGatewayClient.getBaseUrl()
            ));
        } else {
            binding.textSettingsAiBody.setText(R.string.settings_ai_body);
        }
        binding.textSettingsHistoryCount.setText(getString(R.string.settings_history_count, entries.size()));

        if (entries.isEmpty()) {
            binding.textSettingsHistoryLast.setText(R.string.settings_history_none);
        } else {
            binding.textSettingsHistoryLast.setText(
                    getString(R.string.settings_history_last, UiFormatters.formatTimestamp(entries.get(0).getTimestamp())));
        }

        binding.textSettingsHealthBody.setText(R.string.health_connect_loading);
        binding.buttonHealthConnectAction.setEnabled(false);
        binding.buttonHealthConnectManage.setEnabled(false);
        healthConnectManager.loadSnapshot(requireContext(), this::renderHealthState);
    }

    private void renderHealthState(@NonNull HealthConnectSnapshot snapshot) {
        if (binding == null) {
            return;
        }

        healthConnectSnapshot = snapshot;

        if (snapshot.needsProviderUpdate()) {
            binding.textSettingsHealthBody.setText(withHealthDetail(
                    getString(R.string.settings_health_connect_needs_update),
                    snapshot
            ));
            binding.buttonHealthConnectAction.setText(R.string.health_connect_action_install);
            binding.buttonHealthConnectAction.setEnabled(true);
            binding.buttonHealthConnectManage.setEnabled(false);
            return;
        }

        if (!snapshot.isAvailable()) {
            binding.textSettingsHealthBody.setText(withHealthDetail(
                    getString(R.string.settings_health_connect_unavailable),
                    snapshot
            ));
            binding.buttonHealthConnectAction.setText(R.string.health_connect_action_connect);
            binding.buttonHealthConnectAction.setEnabled(false);
            binding.buttonHealthConnectManage.setEnabled(false);
            return;
        }

        if (!snapshot.isPermissionsGranted()) {
            binding.textSettingsHealthBody.setText(withHealthDetail(
                    getString(R.string.settings_health_connect_permissions_needed),
                    snapshot
            ));
            binding.buttonHealthConnectAction.setText(R.string.health_connect_action_connect);
            binding.buttonHealthConnectAction.setEnabled(true);
            binding.buttonHealthConnectManage.setEnabled(true);
            return;
        }

        binding.textSettingsHealthBody.setText(withHealthDetail(
                getString(R.string.settings_health_connect_connected),
                snapshot
        ));
        binding.buttonHealthConnectAction.setText(R.string.health_connect_action_refresh);
        binding.buttonHealthConnectAction.setEnabled(true);
        binding.buttonHealthConnectManage.setEnabled(true);
    }

    private void handleHealthConnectAction() {
        if (binding == null || healthConnectSnapshot == null) {
            return;
        }

        if (healthConnectSnapshot.needsProviderUpdate()) {
            healthConnectManager.openProviderOnboarding(requireContext());
            return;
        }

        if (!healthConnectSnapshot.isAvailable()) {
            Snackbar.make(binding.getRoot(), R.string.settings_health_connect_unavailable, Snackbar.LENGTH_LONG).show();
            return;
        }

        if (!healthConnectSnapshot.isPermissionsGranted()) {
            permissionsLauncher.launch(healthConnectManager.getRequiredPermissions());
            return;
        }

        bindState();
        Snackbar.make(binding.getRoot(), R.string.health_connect_refresh_started, Snackbar.LENGTH_SHORT).show();
    }

    private void openHealthConnectManage() {
        if (binding == null || healthConnectSnapshot == null || !healthConnectSnapshot.isAvailable()) {
            Snackbar.make(binding.getRoot(), R.string.health_connect_manage_unavailable, Snackbar.LENGTH_LONG).show();
            return;
        }

        healthConnectManager.openManageData(requireContext());
    }

    private void confirmClearData() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_clear_confirm_title)
                .setMessage(R.string.settings_clear_confirm_body)
                .setNegativeButton(R.string.settings_clear_cancel_action, null)
                .setPositiveButton(R.string.settings_clear_confirm_action, (dialogInterface, which) -> {
                    repository.clearAll();
                    bindState();
                    Snackbar.make(binding.getRoot(), R.string.settings_cleared, Snackbar.LENGTH_LONG).show();
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @NonNull
    private String withHealthDetail(@NonNull String baseMessage, @NonNull HealthConnectSnapshot snapshot) {
        String detail = snapshot.getErrorMessage();
        if (detail == null || detail.trim().isEmpty()) {
            return baseMessage;
        }
        return baseMessage + "\n" + detail.trim();
    }
}
