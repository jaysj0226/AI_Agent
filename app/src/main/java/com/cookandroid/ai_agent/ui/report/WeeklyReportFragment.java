package com.cookandroid.ai_agent.ui.report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.cookandroid.ai_agent.R;
import com.cookandroid.ai_agent.ai.AiGatewayClient;
import com.cookandroid.ai_agent.data.CheckInEntry;
import com.cookandroid.ai_agent.data.WellnessRepository;
import com.cookandroid.ai_agent.databinding.FragmentWeeklyReportBinding;
import com.cookandroid.ai_agent.domain.GuidanceEngine;
import com.cookandroid.ai_agent.domain.ScoreEngine;
import com.cookandroid.ai_agent.ui.UiFormatters;
import com.google.android.material.card.MaterialCardView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WeeklyReportFragment extends Fragment {

    private static final String NO_ENTRY_LABEL = "\uAE30\uB85D \uC5C6\uC74C";
    private static final String NO_ENTRY_BODY =
            "\uC774 \uB0A0\uC9DC\uC5D0\uB294 \uCCB4\uD06C\uC778 \uAE30\uB85D\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.";
    private static final String NO_ENTRY_HINT =
            "\uCCB4\uD06C\uC778\uC744 \uCD94\uAC00\uD558\uBA74 \uADF8\uB0A0 \uC694\uC57D\uC744 \uC5EC\uAE30\uC11C \uBCFC \uC218 \uC788\uC2B5\uB2C8\uB2E4.";
    private static final String NO_NOTE_LABEL = "\uB0A8\uAE34 \uBA54\uBAA8\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.";
    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy'\uB144' M'\uC6D4'", Locale.KOREA);
    private static final DateTimeFormatter EMPTY_DAY_FORMATTER =
            DateTimeFormatter.ofPattern("M'\uC6D4' d'\uC77C' (E)", Locale.KOREA);

    private FragmentWeeklyReportBinding binding;
    private WellnessRepository repository;
    private final AiGatewayClient aiGatewayClient = new AiGatewayClient();
    private final List<MaterialCardView> calendarDayCards = new ArrayList<>();
    private YearMonth visibleMonth;
    private LocalDate selectedDate;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentWeeklyReportBinding.inflate(inflater, container, false);
        repository = new WellnessRepository(requireContext());
        visibleMonth = YearMonth.now();
        selectedDate = LocalDate.now();

        binding.buttonWeeklyCheckin.setOnClickListener(view ->
                NavHostFragment.findNavController(this).navigate(R.id.nav_checkin));
        binding.buttonPreviousMonth.setOnClickListener(view -> changeVisibleMonth(-1));
        binding.buttonNextMonth.setOnClickListener(view -> changeVisibleMonth(1));
        setupCalendarGrid();
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        bindReport();
    }

    private void bindReport() {
        List<CheckInEntry> entries = repository.getEntries();
        bindCalendar(entries);
        bindWeeklySummary(entries);
    }

    private void bindWeeklySummary(@NonNull List<CheckInEntry> entries) {
        if (entries.isEmpty()) {
            bindEmptyState();
            return;
        }

        int averageScore = ScoreEngine.averageScore(entries, 7);
        GuidanceEngine.WeeklyReport report = GuidanceEngine.buildWeeklyReport(entries);

        binding.textWeeklyAverage.setText(getString(R.string.value_score, averageScore));
        UiFormatters.applyBandChip(requireContext(), binding.chipWeeklyBand, averageScore);
        binding.progressWeeklyAverage.setProgress(averageScore);
        binding.textWeeklyHeadline.setText(report.getHeadline());
        binding.textWeeklyEntriesValue.setText(String.valueOf(ScoreEngine.countEntriesInLastDays(entries, 7)));
        binding.textWeeklyTrendValue.setText(ScoreEngine.getTrendLabel(ScoreEngine.calculateTrendDelta(entries)));
        binding.textWeeklyStressValue.setText(getString(R.string.value_out_of_ten, ScoreEngine.averageStress(entries, 7)));
        binding.textWeeklyPattern.setText(report.getPattern());
        binding.textWeeklyReflection.setText(report.getReflection());
        binding.textWeeklyNextStep.setText(report.getNextStep());
        binding.textWeeklyRecentLog.setText(buildRecentLog(entries));

        String weeklyHash = AiGatewayClient.computeWeeklyHash(entries);
        AiGatewayClient.WeeklyGuidance cached = AiGatewayClient.getCachedWeekly(weeklyHash);
        if (cached != null) {
            binding.textWeeklyHeadline.setText(cached.getHeadline());
            binding.textWeeklyPattern.setText(cached.getPattern());
            binding.textWeeklyReflection.setText(cached.getReflection());
            binding.textWeeklyNextStep.setText(cached.getNextStep());
            binding.textWeeklyAiMode.setText(R.string.report_ai_note_server_live);
        } else if (aiGatewayClient.isConfigured()) {
            binding.textWeeklyAiMode.setText(R.string.report_ai_note_server_pending);
            loadRemoteReport(entries);
        } else {
            binding.textWeeklyAiMode.setText(R.string.report_ai_note);
        }
    }

    private void bindEmptyState() {
        binding.textWeeklyAverage.setText(R.string.value_placeholder);
        UiFormatters.applyNeutralChip(requireContext(), binding.chipWeeklyBand, getString(R.string.trend_building));
        binding.progressWeeklyAverage.setProgress(0);
        binding.textWeeklyHeadline.setText(R.string.report_empty_title);
        binding.textWeeklyEntriesValue.setText("0");
        binding.textWeeklyTrendValue.setText(R.string.trend_building);
        binding.textWeeklyStressValue.setText(getString(R.string.value_out_of_ten, 0));
        binding.textWeeklyPattern.setText(R.string.report_empty_body);
        binding.textWeeklyReflection.setText(R.string.report_ai_note);
        binding.textWeeklyNextStep.setText(R.string.today_empty_body);
        binding.textWeeklyRecentLog.setText(R.string.report_empty_body);
        binding.textWeeklyAiMode.setText(R.string.report_ai_note);
    }

    private void bindCalendar(@NonNull List<CheckInEntry> entries) {
        Map<LocalDate, CheckInEntry> entriesByDate = buildEntriesByDate(entries);
        ensureSelectedDate(entriesByDate);
        binding.textReportMonthLabel.setText(MONTH_FORMATTER.format(visibleMonth.atDay(1)));

        LocalDate firstDayOfMonth = visibleMonth.atDay(1);
        int leadingDays = firstDayOfMonth.getDayOfWeek().getValue() % 7;
        LocalDate firstCellDate = firstDayOfMonth.minusDays(leadingDays);

        for (int index = 0; index < calendarDayCards.size(); index++) {
            MaterialCardView card = calendarDayCards.get(index);
            LocalDate cellDate = firstCellDate.plusDays(index);
            boolean isInMonth = YearMonth.from(cellDate).equals(visibleMonth);
            CheckInEntry entry = isInMonth ? entriesByDate.get(cellDate) : null;
            bindCalendarDay(card, cellDate, isInMonth, entry, entries, entriesByDate);
        }

        bindSelectedDay(entriesByDate.get(selectedDate), entries);
    }

    private void setupCalendarGrid() {
        calendarDayCards.clear();
        binding.layoutCalendarRows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (rowIndex > 0) {
                rowParams.topMargin = dp(4);
            }
            row.setLayoutParams(rowParams);

            for (int columnIndex = 0; columnIndex < 7; columnIndex++) {
                MaterialCardView card = (MaterialCardView) inflater.inflate(
                        R.layout.item_report_calendar_day,
                        row,
                        false
                );
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );
                card.setLayoutParams(cardParams);
                row.addView(card);
                calendarDayCards.add(card);
            }

            binding.layoutCalendarRows.addView(row);
        }
    }

    private void bindCalendarDay(@NonNull MaterialCardView card,
                                 @NonNull LocalDate cellDate,
                                 boolean isInMonth,
                                 @Nullable CheckInEntry entry,
                                 @NonNull List<CheckInEntry> entries,
                                 @NonNull Map<LocalDate, CheckInEntry> entriesByDate) {
        TextView dayNumberView = card.findViewById(R.id.textCalendarDayNumber);
        TextView statusView = card.findViewById(R.id.textCalendarDayStatus);
        View dotView = card.findViewById(R.id.viewCalendarDot);

        if (!isInMonth) {
            card.setOnClickListener(null);
            card.setClickable(false);
            card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.neutral_050));
            card.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.neutral_050));
            dayNumberView.setText("");
            statusView.setText("");
            dotView.setVisibility(View.INVISIBLE);
            return;
        }

        dayNumberView.setText(String.valueOf(cellDate.getDayOfMonth()));
        boolean hasEntry = entry != null;
        statusView.setText(hasEntry ? String.valueOf(ScoreEngine.calculateScore(entry)) : "");
        dotView.setVisibility(hasEntry ? View.VISIBLE : View.INVISIBLE);

        int backgroundColor = ContextCompat.getColor(requireContext(), R.color.white);
        int strokeColor = ContextCompat.getColor(requireContext(), hasEntry ? R.color.primary_100 : R.color.neutral_100);
        int dayTextColor = ContextCompat.getColor(requireContext(), R.color.neutral_900);
        int statusColor = ContextCompat.getColor(requireContext(), hasEntry ? R.color.primary_500 : R.color.neutral_300);

        if (cellDate.equals(selectedDate)) {
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.primary_050);
            strokeColor = ContextCompat.getColor(requireContext(), R.color.primary_500);
            dayTextColor = ContextCompat.getColor(requireContext(), R.color.primary_700);
            statusColor = ContextCompat.getColor(requireContext(), R.color.primary_700);
        }

        card.setCardBackgroundColor(backgroundColor);
        card.setStrokeColor(strokeColor);
        dayNumberView.setTextColor(dayTextColor);
        statusView.setTextColor(statusColor);

        card.setOnClickListener(view -> {
            selectedDate = cellDate;
            for (int index = 0; index < calendarDayCards.size(); index++) {
                MaterialCardView dayCard = calendarDayCards.get(index);
                LocalDate rerenderDate = visibleMonth.atDay(1)
                        .minusDays(visibleMonth.atDay(1).getDayOfWeek().getValue() % 7L)
                        .plusDays(index);
                boolean rerenderInMonth = YearMonth.from(rerenderDate).equals(visibleMonth);
                CheckInEntry rerenderEntry = rerenderInMonth ? entriesByDate.get(rerenderDate) : null;
                bindCalendarDay(dayCard, rerenderDate, rerenderInMonth, rerenderEntry, entries, entriesByDate);
            }
            bindSelectedDay(entriesByDate.get(selectedDate), entries);
        });
    }

    private void bindSelectedDay(@Nullable CheckInEntry entry, @NonNull List<CheckInEntry> entries) {
        if (entry == null) {
            binding.textSelectedDayDate.setText(EMPTY_DAY_FORMATTER.format(selectedDate));
            binding.textSelectedDayScore.setText(R.string.value_placeholder);
            UiFormatters.applyNeutralChip(requireContext(), binding.chipSelectedDayBand, NO_ENTRY_LABEL);
            binding.textSelectedDaySummary.setText(NO_ENTRY_BODY);
            binding.textSelectedDayMetrics.setText(NO_ENTRY_HINT);
            binding.textSelectedDayFocus.setText(NO_ENTRY_HINT);
            binding.textSelectedDayNote.setText(NO_NOTE_LABEL);
            return;
        }

        int score = ScoreEngine.calculateScore(entry);
        binding.textSelectedDayDate.setText(UiFormatters.formatTimestamp(entry.getTimestamp()));
        binding.textSelectedDayScore.setText(getString(R.string.value_score, score));
        UiFormatters.applyBandChip(requireContext(), binding.chipSelectedDayBand, score);
        binding.textSelectedDaySummary.setText(GuidanceEngine.buildTodayInsight(entry, entries));
        binding.textSelectedDayMetrics.setText(buildMetricsSummary(entry));
        binding.textSelectedDayFocus.setText(GuidanceEngine.buildTodayFocus(entry));
        binding.textSelectedDayNote.setText(entry.getNote().isEmpty() ? NO_NOTE_LABEL : entry.getNote());
    }

    @NonNull
    private String buildMetricsSummary(@NonNull CheckInEntry entry) {
        return getString(R.string.checkin_sleep_title) + " " + entry.getSleepQuality() + "/10  |  "
                + getString(R.string.checkin_energy_title) + " " + entry.getEnergy() + "/10  |  "
                + getString(R.string.checkin_stress_title) + " " + entry.getStress() + "/10  |  "
                + getString(R.string.checkin_focus_title) + " " + entry.getFocus() + "/10";
    }

    private void ensureSelectedDate(@NonNull Map<LocalDate, CheckInEntry> entriesByDate) {
        if (selectedDate != null && YearMonth.from(selectedDate).equals(visibleMonth)) {
            return;
        }

        selectedDate = entriesByDate.keySet().stream()
                .filter(date -> YearMonth.from(date).equals(visibleMonth))
                .max(Comparator.naturalOrder())
                .orElse(visibleMonth.atDay(1));
    }

    @NonNull
    private Map<LocalDate, CheckInEntry> buildEntriesByDate(@NonNull List<CheckInEntry> entries) {
        Map<LocalDate, CheckInEntry> entriesByDate = new HashMap<>();
        for (CheckInEntry entry : entries) {
            entriesByDate.put(toLocalDate(entry.getTimestamp()), entry);
        }
        return entriesByDate;
    }

    @NonNull
    private LocalDate toLocalDate(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private void changeVisibleMonth(int delta) {
        visibleMonth = visibleMonth.plusMonths(delta);
        selectedDate = visibleMonth.atDay(1);
        bindCalendar(repository.getEntries());
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    private String buildRecentLog(List<CheckInEntry> entries) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(entries.size(), 5);
        for (int index = 0; index < count; index++) {
            CheckInEntry entry = entries.get(index);
            int score = ScoreEngine.calculateScore(entry);
            builder.append(UiFormatters.formatDay(entry.getTimestamp()))
                    .append(" - ")
                    .append(score)
                    .append(" - ")
                    .append(ScoreEngine.getBandLabel(score));

            if (!entry.getNote().isEmpty()) {
                builder.append(" - ").append(entry.getNote());
            }

            if (index < count - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private void loadRemoteReport(@NonNull List<CheckInEntry> entries) {
        if (!aiGatewayClient.isConfigured()) {
            return;
        }

        aiGatewayClient.fetchWeeklyGuidance(entries, new AiGatewayClient.WeeklyCallback() {
            @Override
            public void onLoaded(@NonNull AiGatewayClient.WeeklyGuidance guidance) {
                if (binding == null) {
                    return;
                }

                binding.textWeeklyHeadline.setText(guidance.getHeadline());
                binding.textWeeklyPattern.setText(guidance.getPattern());
                binding.textWeeklyReflection.setText(guidance.getReflection());
                binding.textWeeklyNextStep.setText(guidance.getNextStep());
                binding.textWeeklyAiMode.setText(R.string.report_ai_note_server_live);
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                if (binding == null) {
                    return;
                }
                binding.textWeeklyAiMode.setText(
                        getString(R.string.report_ai_note_server_error) + "\n" + errorMessage
                );
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
