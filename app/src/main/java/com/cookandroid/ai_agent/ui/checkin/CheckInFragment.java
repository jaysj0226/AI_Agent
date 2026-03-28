package com.cookandroid.ai_agent.ui.checkin;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.cookandroid.ai_agent.MainActivity;
import com.cookandroid.ai_agent.R;
import com.cookandroid.ai_agent.ai.AiGatewayClient;
import com.cookandroid.ai_agent.data.CheckInEntry;
import com.cookandroid.ai_agent.data.WellnessRepository;
import com.cookandroid.ai_agent.databinding.FragmentCheckinBinding;
import com.cookandroid.ai_agent.domain.GuidanceEngine;
import com.cookandroid.ai_agent.domain.ScoreEngine;
import com.cookandroid.ai_agent.ui.UiFormatters;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;

public class CheckInFragment extends Fragment {

    private FragmentCheckinBinding binding;
    private WellnessRepository repository;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCheckinBinding.inflate(inflater, container, false);
        repository = new WellnessRepository(requireContext());

        bindSlider(binding.sliderSleep, binding.textSleepValue);
        bindSlider(binding.sliderEnergy, binding.textEnergyValue);
        bindSlider(binding.sliderStress, binding.textStressValue);
        bindSlider(binding.sliderFocus, binding.textFocusValue);

        prefillLatestEntry();
        binding.textCheckinDate.setText(getString(R.string.checkin_date_label, UiFormatters.formatTimestamp(System.currentTimeMillis())));
        binding.buttonSaveCheckin.setOnClickListener(view -> saveCheckIn());
        binding.editCheckinNote.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        refreshPreview();
        return binding.getRoot();
    }

    private void bindSlider(Slider slider, TextView valueView) {
        valueView.setText(getString(R.string.value_out_of_ten, (int) slider.getValue()));
        slider.addOnChangeListener((s, value, fromUser) -> {
            valueView.setText(getString(R.string.value_out_of_ten, (int) value));
            refreshPreview();
        });
    }

    private void prefillLatestEntry() {
        if (repository.getEntries().isEmpty()) {
            binding.textCheckinHelper.setText(R.string.checkin_helper_default);
            return;
        }

        CheckInEntry latestEntry = repository.getEntries().get(0);
        binding.sliderSleep.setValue(latestEntry.getSleepQuality());
        binding.sliderEnergy.setValue(latestEntry.getEnergy());
        binding.sliderStress.setValue(latestEntry.getStress());
        binding.sliderFocus.setValue(latestEntry.getFocus());
        binding.editCheckinNote.setText(latestEntry.getNote());
        binding.textCheckinHelper.setText(R.string.checkin_helper_prefilled);
    }

    private void refreshPreview() {
        CheckInEntry previewEntry = new CheckInEntry(
                System.currentTimeMillis(),
                (int) binding.sliderSleep.getValue(),
                (int) binding.sliderEnergy.getValue(),
                (int) binding.sliderStress.getValue(),
                (int) binding.sliderFocus.getValue(),
                binding.editCheckinNote.getText() == null ? "" : binding.editCheckinNote.getText().toString());

        int score = ScoreEngine.calculateScore(previewEntry);
        binding.textPreviewScore.setText(getString(R.string.value_score, score));
        UiFormatters.applyBandChip(requireContext(), binding.chipPreviewBand, score);
        binding.progressPreviewScore.setProgress(score);
        binding.textPreviewSummary.setText(GuidanceEngine.buildTodayHeadline(score));
        binding.textPreviewHint.setText(GuidanceEngine.buildTodayFocus(previewEntry));
    }

    private void saveCheckIn() {
        CheckInEntry entry = new CheckInEntry(
                System.currentTimeMillis(),
                (int) binding.sliderSleep.getValue(),
                (int) binding.sliderEnergy.getValue(),
                (int) binding.sliderStress.getValue(),
                (int) binding.sliderFocus.getValue(),
                binding.editCheckinNote.getText() == null ? "" : binding.editCheckinNote.getText().toString());

        repository.saveEntry(entry);
        AiGatewayClient.invalidateCache();
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.showMessage(R.string.checkin_saved);
            activity.openTodayScreen();
        } else {
            Snackbar.make(binding.getRoot(), R.string.checkin_saved, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
