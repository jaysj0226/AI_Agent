package com.cookandroid.ai_agent.data;

import org.json.JSONException;
import org.json.JSONObject;

public class CheckInEntry {

    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_SLEEP = "sleep";
    private static final String KEY_ENERGY = "energy";
    private static final String KEY_STRESS = "stress";
    private static final String KEY_FOCUS = "focus";
    private static final String KEY_NOTE = "note";

    private final long timestamp;
    private final int sleepQuality;
    private final int energy;
    private final int stress;
    private final int focus;
    private final String note;

    public CheckInEntry(long timestamp, int sleepQuality, int energy, int stress, int focus, String note) {
        this.timestamp = timestamp;
        this.sleepQuality = clampMetric(sleepQuality);
        this.energy = clampMetric(energy);
        this.stress = clampMetric(stress);
        this.focus = clampMetric(focus);
        this.note = note == null ? "" : note.trim();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getSleepQuality() {
        return sleepQuality;
    }

    public int getEnergy() {
        return energy;
    }

    public int getStress() {
        return stress;
    }

    public int getFocus() {
        return focus;
    }

    public String getNote() {
        return note;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(KEY_TIMESTAMP, timestamp);
        jsonObject.put(KEY_SLEEP, sleepQuality);
        jsonObject.put(KEY_ENERGY, energy);
        jsonObject.put(KEY_STRESS, stress);
        jsonObject.put(KEY_FOCUS, focus);
        jsonObject.put(KEY_NOTE, note);
        return jsonObject;
    }

    public static CheckInEntry fromJson(JSONObject jsonObject) throws JSONException {
        return new CheckInEntry(
                jsonObject.getLong(KEY_TIMESTAMP),
                jsonObject.getInt(KEY_SLEEP),
                jsonObject.getInt(KEY_ENERGY),
                jsonObject.getInt(KEY_STRESS),
                jsonObject.getInt(KEY_FOCUS),
                jsonObject.optString(KEY_NOTE, ""));
    }

    private static int clampMetric(int value) {
        return Math.max(1, Math.min(10, value));
    }
}
