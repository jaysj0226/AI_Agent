package com.cookandroid.ai_agent.ai;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cookandroid.ai_agent.BuildConfig;
import com.cookandroid.ai_agent.data.CheckInEntry;
import com.cookandroid.ai_agent.domain.ScoreEngine;
import com.cookandroid.ai_agent.health.HealthConnectSnapshot;
import com.cookandroid.ai_agent.ui.UiFormatters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLException;

public class AiGatewayClient {

    public interface TodayCallback {
        void onLoaded(@NonNull TodayGuidance guidance);

        void onError(@NonNull String errorMessage);
    }

    public interface WeeklyCallback {
        void onLoaded(@NonNull WeeklyGuidance guidance);

        void onError(@NonNull String errorMessage);
    }

    public static final class TodayGuidance {
        private final String source;
        private final String summary;
        private final String insight;
        private final String focus;
        private final String disclaimer;

        public TodayGuidance(String source, String summary, String insight, String focus, String disclaimer) {
            this.source = source;
            this.summary = summary;
            this.insight = insight;
            this.focus = focus;
            this.disclaimer = disclaimer;
        }

        public String getSource() {
            return source;
        }

        public String getSummary() {
            return summary;
        }

        public String getInsight() {
            return insight;
        }

        public String getFocus() {
            return focus;
        }

        public String getDisclaimer() {
            return disclaimer;
        }
    }

    public static final class WeeklyGuidance {
        private final String source;
        private final String headline;
        private final String pattern;
        private final String reflection;
        private final String nextStep;
        private final String disclaimer;

        public WeeklyGuidance(String source,
                              String headline,
                              String pattern,
                              String reflection,
                              String nextStep,
                              String disclaimer) {
            this.source = source;
            this.headline = headline;
            this.pattern = pattern;
            this.reflection = reflection;
            this.nextStep = nextStep;
            this.disclaimer = disclaimer;
        }

        public String getSource() {
            return source;
        }

        public String getHeadline() {
            return headline;
        }

        public String getPattern() {
            return pattern;
        }

        public String getReflection() {
            return reflection;
        }

        public String getNextStep() {
            return nextStep;
        }

        public String getDisclaimer() {
            return disclaimer;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Object CACHE_LOCK = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // --- Today cache ---
    private static volatile TodayGuidance cachedTodayGuidance;
    private static volatile long cachedTodayTimestamp = -1;
    private static volatile long todayInFlightTimestamp = -1;
    private static volatile long todayRequestGeneration = 0;

    // --- Weekly cache ---
    private static volatile WeeklyGuidance cachedWeeklyGuidance;
    private static volatile String cachedWeeklyHash = "";
    private static volatile String weeklyInFlightHash = "";
    private static volatile long weeklyRequestGeneration = 0;

    public static void invalidateCache() {
        synchronized (CACHE_LOCK) {
            cachedTodayTimestamp = -1;
            cachedTodayGuidance = null;
            todayInFlightTimestamp = -1;
            todayRequestGeneration++;

            cachedWeeklyHash = "";
            cachedWeeklyGuidance = null;
            weeklyInFlightHash = "";
            weeklyRequestGeneration++;
        }
    }

    @Nullable
    public static TodayGuidance getCachedToday(long entryTimestamp) {
        synchronized (CACHE_LOCK) {
            if (entryTimestamp == cachedTodayTimestamp && cachedTodayGuidance != null) {
                return cachedTodayGuidance;
            }
            return null;
        }
    }

    @Nullable
    public static WeeklyGuidance getCachedWeekly(@NonNull String hash) {
        synchronized (CACHE_LOCK) {
            if (hash.equals(cachedWeeklyHash) && cachedWeeklyGuidance != null) {
                return cachedWeeklyGuidance;
            }
            return null;
        }
    }

    @NonNull
    public static String computeWeeklyHash(@NonNull List<CheckInEntry> entries) {
        return buildWeeklyHash(entries);
    }

    public boolean isConfigured() {
        return !getBaseUrl().isEmpty();
    }

    @NonNull
    public String getBaseUrl() {
        return BuildConfig.AI_SERVER_BASE_URL == null ? "" : BuildConfig.AI_SERVER_BASE_URL.trim();
    }

    @NonNull
    public String getSharedToken() {
        return BuildConfig.AI_SERVER_SHARED_TOKEN == null ? "" : BuildConfig.AI_SERVER_SHARED_TOKEN.trim();
    }

    public void fetchTodayGuidance(@NonNull CheckInEntry latestEntry,
                                   @NonNull List<CheckInEntry> entries,
                                   @Nullable HealthConnectSnapshot snapshot,
                                   @NonNull TodayCallback callback) {
        if (!isConfigured()) {
            mainHandler.post(() -> callback.onError("AI server URL is missing."));
            return;
        }

        long entryTimestamp = latestEntry.getTimestamp();
        final long requestGeneration;
        synchronized (CACHE_LOCK) {
            if (entryTimestamp == cachedTodayTimestamp && cachedTodayGuidance != null) {
                TodayGuidance guidance = cachedTodayGuidance;
                mainHandler.post(() -> callback.onLoaded(guidance));
                return;
            }

            if (todayInFlightTimestamp == entryTimestamp) {
                return;
            }
            todayInFlightTimestamp = entryTimestamp;
            requestGeneration = todayRequestGeneration;
        }

        EXECUTOR.execute(() -> {
            try {
                JSONObject request = buildTodayRequest(latestEntry, entries, snapshot);
                JSONObject response = postJson("/v1/ai/today-guidance", request);
                TodayGuidance guidance = new TodayGuidance(
                        response.optString("source", "fallback"),
                        response.optString("summary", ""),
                        response.optString("insight", ""),
                        response.optString("focus", ""),
                        response.optString("disclaimer", "")
                );
                boolean isCurrentRequest;
                synchronized (CACHE_LOCK) {
                    isCurrentRequest = requestGeneration == todayRequestGeneration;
                    if (todayInFlightTimestamp == entryTimestamp) {
                        todayInFlightTimestamp = -1;
                    }
                    if (isCurrentRequest) {
                        cachedTodayGuidance = guidance;
                        cachedTodayTimestamp = entryTimestamp;
                    }
                }
                if (!isCurrentRequest) {
                    return;
                }
                mainHandler.post(() -> callback.onLoaded(guidance));
            } catch (Exception exception) {
                boolean isCurrentRequest;
                synchronized (CACHE_LOCK) {
                    isCurrentRequest = requestGeneration == todayRequestGeneration;
                    if (todayInFlightTimestamp == entryTimestamp) {
                        todayInFlightTimestamp = -1;
                    }
                }
                if (!isCurrentRequest) {
                    return;
                }
                String errorMessage = createUserFacingError(exception);
                mainHandler.post(() -> callback.onError(errorMessage));
            }
        });
    }

    public void fetchWeeklyGuidance(@NonNull List<CheckInEntry> entries, @NonNull WeeklyCallback callback) {
        if (!isConfigured()) {
            mainHandler.post(() -> callback.onError("AI server URL is missing."));
            return;
        }

        String hash = buildWeeklyHash(entries);
        final long requestGeneration;
        synchronized (CACHE_LOCK) {
            if (hash.equals(cachedWeeklyHash) && cachedWeeklyGuidance != null) {
                WeeklyGuidance guidance = cachedWeeklyGuidance;
                mainHandler.post(() -> callback.onLoaded(guidance));
                return;
            }

            if (hash.equals(weeklyInFlightHash)) {
                return;
            }
            weeklyInFlightHash = hash;
            requestGeneration = weeklyRequestGeneration;
        }

        EXECUTOR.execute(() -> {
            try {
                JSONObject request = buildWeeklyRequest(entries);
                JSONObject response = postJson("/v1/ai/weekly-report", request);
                WeeklyGuidance guidance = new WeeklyGuidance(
                        response.optString("source", "fallback"),
                        response.optString("headline", ""),
                        response.optString("pattern", ""),
                        response.optString("reflection", ""),
                        response.optString("next_step", ""),
                        response.optString("disclaimer", "")
                );
                boolean isCurrentRequest;
                synchronized (CACHE_LOCK) {
                    isCurrentRequest = requestGeneration == weeklyRequestGeneration;
                    if (hash.equals(weeklyInFlightHash)) {
                        weeklyInFlightHash = "";
                    }
                    if (isCurrentRequest) {
                        cachedWeeklyGuidance = guidance;
                        cachedWeeklyHash = hash;
                    }
                }
                if (!isCurrentRequest) {
                    return;
                }
                mainHandler.post(() -> callback.onLoaded(guidance));
            } catch (Exception exception) {
                boolean isCurrentRequest;
                synchronized (CACHE_LOCK) {
                    isCurrentRequest = requestGeneration == weeklyRequestGeneration;
                    if (hash.equals(weeklyInFlightHash)) {
                        weeklyInFlightHash = "";
                    }
                }
                if (!isCurrentRequest) {
                    return;
                }
                String errorMessage = createUserFacingError(exception);
                mainHandler.post(() -> callback.onError(errorMessage));
            }
        });
    }

    @NonNull
    private static String buildWeeklyHash(@NonNull List<CheckInEntry> entries) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(entries.size(), 5);
        for (int index = 0; index < count; index++) {
            CheckInEntry entry = entries.get(index);
            builder.append(entry.getTimestamp()).append(':')
                    .append(ScoreEngine.calculateScore(entry)).append(':')
                    .append(entry.getNote()).append('|');
        }
        return builder.toString();
    }

    @NonNull
    private JSONObject buildTodayRequest(@NonNull CheckInEntry latestEntry,
                                         @NonNull List<CheckInEntry> entries,
                                         @Nullable HealthConnectSnapshot snapshot) throws Exception {
        int latestScore = ScoreEngine.calculateScore(latestEntry);
        JSONObject request = new JSONObject();
        request.put("locale", "ko-KR");
        request.put("deterministic_score", latestScore);
        request.put("score_band", getScoreBandKey(latestScore));

        JSONObject metrics = new JSONObject();
        metrics.put("sleep_quality", latestEntry.getSleepQuality());
        metrics.put("energy", latestEntry.getEnergy());
        metrics.put("stress", latestEntry.getStress());
        metrics.put("focus", latestEntry.getFocus());
        request.put("metrics", metrics);

        JSONObject weekContext = new JSONObject();
        weekContext.put("entry_count_last_7_days", ScoreEngine.countEntriesInLastDays(entries, 7));
        weekContext.put("weekly_average_score", ScoreEngine.averageScore(entries, 7));
        weekContext.put("trend", getTrendKey(ScoreEngine.calculateTrendDelta(entries)));
        request.put("week_context", weekContext);

        request.put("diary_note", latestEntry.getNote());

        JSONObject healthContext = new JSONObject();
        healthContext.put("provider_available", snapshot != null && snapshot.isAvailable());
        healthContext.put("permissions_granted", snapshot != null && snapshot.isPermissionsGranted());
        request.put("health_context", healthContext);
        return request;
    }

    @NonNull
    private JSONObject buildWeeklyRequest(@NonNull List<CheckInEntry> entries) throws Exception {
        JSONObject request = new JSONObject();
        request.put("locale", "ko-KR");
        request.put("average_score", ScoreEngine.averageScore(entries, 7));
        request.put("trend", getTrendKey(ScoreEngine.calculateTrendDelta(entries)));
        request.put("entry_count_last_7_days", ScoreEngine.countEntriesInLastDays(entries, 7));
        request.put("average_stress", ScoreEngine.averageStress(entries, 7));

        JSONArray recentEntries = new JSONArray();
        int count = Math.min(entries.size(), 5);
        for (int index = 0; index < count; index++) {
            CheckInEntry entry = entries.get(index);
            JSONObject item = new JSONObject();
            item.put("date_label", UiFormatters.formatDay(entry.getTimestamp()));
            item.put("score", ScoreEngine.calculateScore(entry));
            item.put("note", entry.getNote());
            recentEntries.put(item);
        }
        request.put("recent_entries", recentEntries);
        return request;
    }

    @NonNull
    private JSONObject postJson(@NonNull String path, @NonNull JSONObject body) throws Exception {
        String normalizedBaseUrl = getBaseUrl();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(normalizedBaseUrl + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(BuildConfig.AI_SERVER_TIMEOUT_MILLIS);
        connection.setReadTimeout(BuildConfig.AI_SERVER_TIMEOUT_MILLIS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        if (!getSharedToken().isEmpty()) {
            connection.setRequestProperty("X-App-Token", getSharedToken());
        }

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload);
        }

        int responseCode = connection.getResponseCode();
        InputStream responseStream = responseCode >= 200 && responseCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String responseBody = readStream(responseStream);
        connection.disconnect();

        if (responseCode < 200 || responseCode >= 300) {
            String detail = extractServerError(responseBody);
            if (detail.isEmpty()) {
                detail = "Empty error body";
            }
            throw new IllegalStateException("HTTP " + responseCode + ": " + detail);
        }
        return new JSONObject(responseBody);
    }

    @NonNull
    private String extractServerError(@Nullable String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return "";
        }

        try {
            JSONObject jsonObject = new JSONObject(responseBody);
            Object detail = jsonObject.opt("detail");
            if (detail instanceof JSONArray) {
                return ((JSONArray) detail).toString();
            }
            if (detail != null) {
                return String.valueOf(detail);
            }
        } catch (Exception ignored) {
            // Fall through and return the raw body when the error is not JSON.
        }
        return responseBody.trim();
    }

    @NonNull
    private String readStream(@Nullable InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }

        try (InputStream stream = inputStream;
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    @NonNull
    private String getScoreBandKey(int score) {
        if (score >= 70) {
            return "good";
        }
        if (score >= 45) {
            return "fair";
        }
        return "low";
    }

    @NonNull
    private String getTrendKey(int delta) {
        if (delta >= 8) {
            return "improving";
        }
        if (delta <= -8) {
            return "slipping";
        }
        return "stable";
    }

    @NonNull
    private String createUserFacingError(@NonNull Exception exception) {
        if (exception instanceof UnknownHostException) {
            return "Server address could not be resolved. Check Tailscale connectivity on this device.";
        }
        if (exception instanceof SocketTimeoutException) {
            return "The AI server timed out after " + BuildConfig.AI_SERVER_TIMEOUT_MILLIS + " ms.";
        }
        if (exception instanceof SSLException) {
            return "HTTPS/TLS handshake failed while connecting to the AI server.";
        }
        String message = exception.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message.trim();
        }
        return exception.getClass().getSimpleName();
    }
}
