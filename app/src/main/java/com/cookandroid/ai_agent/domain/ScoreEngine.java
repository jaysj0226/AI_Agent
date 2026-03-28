package com.cookandroid.ai_agent.domain;

import com.cookandroid.ai_agent.data.CheckInEntry;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ScoreEngine {

    private static final int GOOD_THRESHOLD = 70;
    private static final int FAIR_THRESHOLD = 45;

    private ScoreEngine() {
    }

    public static int calculateScore(CheckInEntry entry) {
        float sleepScore = entry.getSleepQuality() * 2.4f;
        float energyScore = entry.getEnergy() * 3.0f;
        float focusScore = entry.getFocus() * 2.2f;
        float stressScore = (10 - entry.getStress()) * 2.4f;
        return clampScore(Math.round(sleepScore + energyScore + focusScore + stressScore));
    }

    public static int averageScore(List<CheckInEntry> entries, int limit) {
        if (entries.isEmpty() || limit <= 0) {
            return 0;
        }

        int count = Math.min(entries.size(), limit);
        int total = 0;
        for (int index = 0; index < count; index++) {
            total += calculateScore(entries.get(index));
        }
        return Math.round(total / (float) count);
    }

    public static int averageStress(List<CheckInEntry> entries, int limit) {
        return averageMetric(entries, limit, new MetricSelector() {
            @Override
            public int select(CheckInEntry entry) {
                return entry.getStress();
            }
        });
    }

    public static int countEntriesInLastDays(List<CheckInEntry> entries, int days) {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        int count = 0;
        for (CheckInEntry entry : entries) {
            if (entry.getTimestamp() >= cutoff) {
                count++;
            }
        }
        return count;
    }

    public static int calculateTrendDelta(List<CheckInEntry> entries) {
        if (entries.size() < 2) {
            return 0;
        }

        int latestWindow = Math.min(3, entries.size());
        int priorWindow = Math.min(3, Math.max(0, entries.size() - latestWindow));
        if (priorWindow == 0) {
            return 0;
        }

        int latestAverage = averageScore(entries.subList(0, latestWindow), latestWindow);
        int priorAverage = averageScore(entries.subList(latestWindow, latestWindow + priorWindow), priorWindow);
        return latestAverage - priorAverage;
    }

    public static String getBandLabel(int score) {
        if (score >= GOOD_THRESHOLD) {
            return "좋음";
        }
        if (score >= FAIR_THRESHOLD) {
            return "보통";
        }
        return "낮음";
    }

    public static String getTrendLabel(int delta) {
        if (delta >= 8) {
            return "개선 중";
        }
        if (delta <= -8) {
            return "하락 중";
        }
        return "안정적";
    }

    private static int averageMetric(List<CheckInEntry> entries, int limit, MetricSelector selector) {
        if (entries.isEmpty() || limit <= 0) {
            return 0;
        }

        int count = Math.min(entries.size(), limit);
        int total = 0;
        for (int index = 0; index < count; index++) {
            total += selector.select(entries.get(index));
        }
        return Math.round(total / (float) count);
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private interface MetricSelector {
        int select(CheckInEntry entry);
    }
}
