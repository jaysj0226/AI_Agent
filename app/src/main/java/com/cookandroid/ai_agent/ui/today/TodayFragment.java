package com.cookandroid.ai_agent.ui.today;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.cookandroid.ai_agent.R;
import com.cookandroid.ai_agent.ai.AiGatewayClient;
import com.cookandroid.ai_agent.data.CheckInEntry;
import com.cookandroid.ai_agent.data.WellnessRepository;
import com.cookandroid.ai_agent.databinding.FragmentTodayBinding;
import com.cookandroid.ai_agent.domain.GuidanceEngine;
import com.cookandroid.ai_agent.domain.ScoreEngine;
import com.cookandroid.ai_agent.health.HealthConnectManager;
import com.cookandroid.ai_agent.health.HealthConnectSnapshot;
import com.cookandroid.ai_agent.ui.UiFormatters;

import java.util.List;

public class TodayFragment extends Fragment {

    private FragmentTodayBinding binding;
    private WellnessRepository repository;
    private final HealthConnectManager healthConnectManager = new HealthConnectManager();
    private final AiGatewayClient aiGatewayClient = new AiGatewayClient();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentTodayBinding.inflate(inflater, container, false);
        repository = new WellnessRepository(requireContext());
        binding.buttonTodayCheckin.setOnClickListener(view ->
                NavHostFragment.findNavController(this).navigate(R.id.nav_checkin));
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        bindContent();
    }

    private void bindContent() {
        List<CheckInEntry> entries = repository.getEntries();
        if (entries.isEmpty()) {
            bindEmptyState();
        } else {
            CheckInEntry latestEntry = entries.get(0);
            int score = ScoreEngine.calculateScore(latestEntry);

            binding.textTodayScore.setText(String.valueOf(score));
            UiFormatters.applyBandChip(requireContext(), binding.chipTodayBand, score);
            binding.progressTodayScore.setProgress(score);
            binding.textTodayScoreSummary.setText(GuidanceEngine.buildTodayHeadline(score));
            binding.textTodayInsight.setText(GuidanceEngine.buildTodayInsight(latestEntry, entries));
            binding.textTodayFocus.setText(GuidanceEngine.buildTodayFocus(latestEntry));
            binding.textTodayLastCheckin.setText(getString(R.string.today_last_checkin, UiFormatters.formatTimestamp(latestEntry.getTimestamp())));
            binding.textTodayWeekCount.setText(getString(R.string.today_week_count, ScoreEngine.countEntriesInLastDays(entries, 7)));
            bindMetricGrid(latestEntry);

            AiGatewayClient.TodayGuidance cached = AiGatewayClient.getCachedToday(latestEntry.getTimestamp());
            if (cached != null) {
                binding.textTodayScoreSummary.setText(cached.getSummary());
                if (!cached.getInsight().isEmpty()) {
                    binding.textTodayInsight.setText(cached.getInsight());
                }
                binding.textTodayFocus.setText(cached.getFocus());
                binding.textTodayAiMode.setText(R.string.today_ai_mode_server_live);
            } else if (aiGatewayClient.isConfigured()) {
                binding.textTodayAiMode.setText(R.string.today_ai_mode_server_pending);
                loadRemoteGuidance(latestEntry, entries);
            } else {
                binding.textTodayAiMode.setText(R.string.today_ai_mode);
            }
        }
        loadHealthConnectState();
    }

    private void bindMetricGrid(@NonNull CheckInEntry entry) {
        binding.textMetricSleep.setText(getString(R.string.value_out_of_ten, entry.getSleepQuality()));
        binding.textMetricEnergy.setText(getString(R.string.value_out_of_ten, entry.getEnergy()));
        binding.textMetricStress.setText(getString(R.string.value_out_of_ten, entry.getStress()));
        binding.textMetricFocus.setText(getString(R.string.value_out_of_ten, entry.getFocus()));
    }

    private void bindEmptyState() {
        binding.textTodayScore.setText(R.string.value_placeholder);
        UiFormatters.applyNeutralChip(requireContext(), binding.chipTodayBand, getString(R.string.score_label_building));
        binding.progressTodayScore.setProgress(0);
        binding.textTodayScoreSummary.setText(R.string.today_empty_title);
        binding.textTodayInsight.setText(R.string.today_empty_body);
        binding.textTodayFocus.setText(R.string.today_source_body);
        binding.textTodayLastCheckin.setText(R.string.today_no_check_in);
        binding.textTodayWeekCount.setText(getString(R.string.today_week_count, 0));
        binding.textTodayAiMode.setText(aiGatewayClient.isConfigured()
                ? R.string.today_ai_mode_server_pending
                : R.string.today_ai_mode);

        binding.textMetricSleep.setText(R.string.value_placeholder);
        binding.textMetricEnergy.setText(R.string.value_placeholder);
        binding.textMetricStress.setText(R.string.value_placeholder);
        binding.textMetricFocus.setText(R.string.value_placeholder);
    }

    private void loadHealthConnectState() {
        binding.textTodayHealthConnect.setText(R.string.health_connect_loading);
        healthConnectManager.loadSnapshot(requireContext(), this::renderHealthConnectState);
    }

    private void renderHealthConnectState(@NonNull HealthConnectSnapshot snapshot) {
        if (binding == null) {
            return;
        }

        if (snapshot.needsProviderUpdate()) {
            binding.textTodayHealthConnect.setText(withHealthDetail(
                    getString(R.string.today_health_connect_needs_update),
                    snapshot
            ));
            return;
        }

        if (!snapshot.isAvailable()) {
            binding.textTodayHealthConnect.setText(withHealthDetail(
                    getString(R.string.today_health_connect_unavailable),
                    snapshot
            ));
            return;
        }

        if (!snapshot.isPermissionsGranted()) {
            binding.textTodayHealthConnect.setText(withHealthDetail(
                    getString(R.string.today_health_connect_permissions_needed),
                    snapshot
            ));
            return;
        }

        binding.textTodayHealthConnect.setText(withHealthDetail(
                getString(R.string.today_health_connect_connected),
                snapshot
        ));
    }

    private void loadRemoteGuidance(@NonNull CheckInEntry latestEntry, @NonNull List<CheckInEntry> entries) {
        if (!aiGatewayClient.isConfigured()) {
            return;
        }

        aiGatewayClient.fetchTodayGuidance(latestEntry, entries, null, new AiGatewayClient.TodayCallback() {
            @Override
            public void onLoaded(@NonNull AiGatewayClient.TodayGuidance guidance) {
                if (binding == null) {
                    return;
                }

                binding.textTodayScoreSummary.setText(guidance.getSummary());
                if (!guidance.getInsight().isEmpty()) {
                    binding.textTodayInsight.setText(guidance.getInsight());
                }
                binding.textTodayFocus.setText(guidance.getFocus());
                binding.textTodayAiMode.setText(R.string.today_ai_mode_server_live);
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                if (binding == null) {
                    return;
                }
                binding.textTodayAiMode.setText(
                        getString(R.string.today_ai_mode_server_error) + "\n" + errorMessage
                );
            }
        });
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
