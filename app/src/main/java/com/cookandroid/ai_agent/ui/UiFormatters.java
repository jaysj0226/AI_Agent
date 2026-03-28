package com.cookandroid.ai_agent.ui;

import android.content.Context;
import android.content.res.ColorStateList;

import androidx.core.content.ContextCompat;

import com.cookandroid.ai_agent.R;
import com.cookandroid.ai_agent.domain.ScoreEngine;
import com.google.android.material.chip.Chip;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class UiFormatters {

    private static final Locale APP_LOCALE = Locale.KOREA;

    private UiFormatters() {
    }

    public static void applyBandChip(Context context, Chip chip, int score) {
        chip.setText(ScoreEngine.getBandLabel(score));
        chip.setChipBackgroundColor(ColorStateList.valueOf(
                ContextCompat.getColor(context, getBandColorRes(score))));
        chip.setTextColor(ContextCompat.getColor(context, R.color.white));
    }

    public static void applyNeutralChip(Context context, Chip chip, String label) {
        chip.setText(label);
        chip.setChipBackgroundColor(ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.neutral_100)));
        chip.setTextColor(ContextCompat.getColor(context, R.color.neutral_500));
    }

    public static String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("M월 d일 (E) a h:mm", APP_LOCALE)
                .format(new Date(timestamp));
    }

    public static String formatDay(long timestamp) {
        return new SimpleDateFormat("E", APP_LOCALE).format(new Date(timestamp));
    }

    public static String formatWholeNumber(long value) {
        return NumberFormat.getIntegerInstance(APP_LOCALE).format(value);
    }

    public static String formatSleepDuration(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours <= 0) {
            return minutes + "분";
        }
        if (minutes == 0) {
            return hours + "시간";
        }
        return hours + "시간 " + minutes + "분";
    }

    private static int getBandColorRes(int score) {
        if (score >= 70) {
            return R.color.score_good;
        }
        if (score >= 45) {
            return R.color.score_fair;
        }
        return R.color.score_low;
    }
}
