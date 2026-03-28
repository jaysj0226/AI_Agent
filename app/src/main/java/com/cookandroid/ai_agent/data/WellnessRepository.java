package com.cookandroid.ai_agent.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WellnessRepository {

    private static final String PREFS_NAME = "wellness_repository";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 28;

    private final SharedPreferences sharedPreferences;

    public WellnessRepository(Context context) {
        sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized List<CheckInEntry> getEntries() {
        String rawEntries = sharedPreferences.getString(KEY_ENTRIES, "[]");
        List<CheckInEntry> entries = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(rawEntries);
            for (int index = 0; index < jsonArray.length(); index++) {
                entries.add(CheckInEntry.fromJson(jsonArray.getJSONObject(index)));
            }
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }

        Collections.sort(entries, new Comparator<CheckInEntry>() {
            @Override
            public int compare(CheckInEntry first, CheckInEntry second) {
                return Long.compare(second.getTimestamp(), first.getTimestamp());
            }
        });
        return entries;
    }

    public synchronized void saveEntry(CheckInEntry entry) {
        List<CheckInEntry> entries = getEntries();
        if (!entries.isEmpty() && isSameDay(entries.get(0).getTimestamp(), entry.getTimestamp())) {
            entries.set(0, entry);
        } else {
            entries.add(0, entry);
        }
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
        }
        persist(entries);
    }

    public synchronized void clearAll() {
        sharedPreferences.edit().remove(KEY_ENTRIES).apply();
    }

    private void persist(List<CheckInEntry> entries) {
        JSONArray jsonArray = new JSONArray();
        for (CheckInEntry entry : entries) {
            try {
                jsonArray.put(entry.toJson());
            } catch (JSONException ignored) {
                // Skip malformed entries instead of failing the entire local save.
            }
        }
        sharedPreferences.edit().putString(KEY_ENTRIES, jsonArray.toString()).apply();
    }

    private boolean isSameDay(long firstTimestamp, long secondTimestamp) {
        Calendar first = Calendar.getInstance();
        first.setTimeInMillis(firstTimestamp);
        Calendar second = Calendar.getInstance();
        second.setTimeInMillis(secondTimestamp);

        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }
}
